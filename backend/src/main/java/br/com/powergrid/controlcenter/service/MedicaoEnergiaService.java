package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.model.MedicaoEnergiaAlimentador;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.repository.MedicaoEnergiaAlimentadorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicaoEnergiaService {
    private final AlimentadorRepository alimentadores;
    private final FonteMedicaoRepository fontes;
    private final MedicaoEnergiaAlimentadorRepository medicoes;
    private final FonteMedicaoService comunicacao;
    private final Clock clock;

    public MedicaoEnergiaService(AlimentadorRepository alimentadores, FonteMedicaoRepository fontes,
            MedicaoEnergiaAlimentadorRepository medicoes, FonteMedicaoService comunicacao, Clock clock) {
        this.alimentadores = alimentadores; this.fontes = fontes; this.medicoes = medicoes;
        this.comunicacao = comunicacao; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public AlimentadorParametroResponseDTO configurar(Long id, AlimentadorParametroRequestDTO request) {
        var alimentador = bloquear(id);
        alimentador.setPercentualPerdaTecnica(request.percentualPerdaTecnica());
        return new AlimentadorParametroResponseDTO(id, alimentador.getPercentualPerdaTecnica());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public FonteAlimentadorResponseDTO criarFonte(Long id, FonteMedicaoRequestDTO request) {
        var alimentador = bloquear(id);
        if (request.subestacaoReferenciaId() != null) throw invalido("Referência de subestação é exclusiva de fontes de linhas.");
        if (fontes.existsByIdentificador(request.identificador())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identificador de fonte já cadastrado.");
        }
        var fonte = new FonteMedicao(); fonte.setAtivo(alimentador); fonte.setIdentificador(request.identificador());
        fonte.setOrigem(request.origem()); fonte.setMonitoradaDesde(clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        fontes.saveAndFlush(fonte);
        return new FonteAlimentadorResponseDTO(fonte.getId(), fonte.getIdentificador(), id, fonte.getOrigem());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public MedicaoEnergiaResponseDTO ingerir(Long id, Long fonteId, MedicaoEnergiaRequestDTO request) {
        var alimentador = bloquear(id);
        var fonte = fontes.buscarPorIdComBloqueio(fonteId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte de medição não encontrada."));
        if (!fonte.getAtivo().getId().equals(id)) throw invalido("A fonte não pertence ao alimentador informado.");
        var instanteServidor = clock.instant();
        if (request.inicioPeriodo() == null || request.fimPeriodo() == null
                || !request.inicioPeriodo().isBefore(request.fimPeriodo())
                || request.inicioPeriodo().toInstant().isAfter(instanteServidor)
                || request.fimPeriodo().toInstant().isAfter(instanteServidor)) {
            throw invalido("Informe um período encerrado, com início anterior ao fim e sem instantes futuros.");
        }
        var inicio = request.inicioPeriodo().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var fim = request.fimPeriodo().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!inicio.isBefore(fim)) throw invalido("O período precisa ser crescente na precisão de microssegundos.");
        if (request.energiaInjetadaMwh() == null || request.energiaInjetadaMwh().signum() < 0) {
            throw invalido("Energia injetada deve ser informada e não negativa.");
        }
        if (alimentador.getPercentualPerdaTecnica() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configure o percentual de perda técnica do alimentador antes da ingestão.");
        }
        if (medicoes.existeSobreposicaoPorAlimentador(id, inicio, fim)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O período se sobrepõe a uma medição do alimentador.");
        }
        var medicao = new MedicaoEnergiaAlimentador(); medicao.setFonte(fonte);
        medicao.setInicioPeriodo(inicio); medicao.setFimPeriodo(fim);
        medicao.setRecebidoEm(instanteServidor.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        medicao.setEnergiaInjetadaMwh(request.energiaInjetadaMwh());
        medicao.setPercentualPerdaReferencia(alimentador.getPercentualPerdaTecnica());
        medicoes.saveAndFlush(medicao);
        comunicacao.registrarComunicacaoValida(fonteId, medicao.getRecebidoEm());
        return new MedicaoEnergiaResponseDTO(medicao.getId(), id, fonteId, fonte.getOrigem(), inicio, fim,
                medicao.getRecebidoEm(), medicao.getEnergiaInjetadaMwh(), medicao.getPercentualPerdaReferencia());
    }

    private Alimentador bloquear(Long id) {
        return alimentadores.buscarPorIdComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado."));
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
