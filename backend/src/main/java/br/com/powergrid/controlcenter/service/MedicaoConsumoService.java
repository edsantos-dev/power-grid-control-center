package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicaoConsumoService {
    private final MedidorInteligenteRepository medidores;
    private final FonteMedicaoRepository fontes;
    private final MedicaoConsumoMedidorRepository medicoes;
    private final FonteMedicaoService comunicacao;
    private final Clock clock;

    public MedicaoConsumoService(MedidorInteligenteRepository medidores, FonteMedicaoRepository fontes,
            MedicaoConsumoMedidorRepository medicoes, FonteMedicaoService comunicacao, Clock clock) {
        this.medidores = medidores; this.fontes = fontes; this.medicoes = medicoes; this.comunicacao = comunicacao; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public FonteMedidorResponseDTO criarFonte(Long id, FonteMedidorRequestDTO request) {
        var medidor = medidores.buscarPorIdComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Medidor inexistente ou sem instalação válida."));
        validarInstalacao(medidor);
        var fonte = new FonteMedicao(); fonte.setAtivo(medidor); fonte.setOrigem(request.origem());
        fonte.setIdentificador("MEDIDOR-" + UUID.randomUUID());
        fonte.setMonitoradaDesde(clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        fontes.saveAndFlush(fonte);
        return new FonteMedidorResponseDTO(fonte.getId(), id, fonte.getIdentificador(), fonte.getOrigem(), fonte.getMonitoradaDesde(), null);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public MedicaoConsumoResponseDTO ingerir(Long id, MedicaoConsumoRequestDTO request) {
        var medidor = medidores.buscarPorIdComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Medidor não encontrado."));
        validarInstalacao(medidor);
        var fonte = fontes.buscarPorIdComBloqueio(request.fonteId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte de medição não encontrada."));
        if (!fonte.getAtivo().getId().equals(id)) throw invalido("A fonte não pertence ao medidor informado.");
        var agora = clock.instant();
        if (request.inicioPeriodo() == null || request.fimPeriodo() == null
                || !request.inicioPeriodo().toInstant().isBefore(request.fimPeriodo().toInstant())
                || request.inicioPeriodo().toInstant().isAfter(agora) || request.fimPeriodo().toInstant().isAfter(agora)) {
            throw invalido("Informe um período encerrado, crescente e sem instantes futuros.");
        }
        var inicio = request.inicioPeriodo().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var fim = request.fimPeriodo().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (!inicio.isBefore(fim)) throw invalido("O período precisa ser crescente na precisão de microssegundos.");
        if (inicio.toLocalDate().isBefore(medidor.getDataInstalacao())) {
            throw conflito("O período não pode anteceder a data de instalação do medidor em UTC.");
        }
        if (request.consumoKwh() == null || request.consumoKwh().signum() < 0) throw invalido("Consumo deve ser informado e não negativo.");
        if (medicoes.existeSobreposicaoPorMedidor(id, inicio, fim)) throw conflito("O período se sobrepõe a uma medição do medidor.");
        var cadastro = medidores.buscarComTopologia(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medidor não encontrado."));
        var transformador = cadastro.getTransformador(); var alimentador = transformador.getAlimentador();
        var medicao = new MedicaoConsumoMedidor(); medicao.setFonte(fonte);
        medicao.setInicioPeriodo(inicio); medicao.setFimPeriodo(fim);
        medicao.setRecebidoEm(agora.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)); medicao.setConsumoKwh(request.consumoKwh());
        medicao.setConsumidorId(cadastro.getConsumidor().getId()); medicao.setTransformadorId(transformador.getId());
        medicao.setAlimentadorId(alimentador.getId()); medicao.setSubestacaoId(alimentador.getSubestacao().getId());
        medicoes.saveAndFlush(medicao);
        comunicacao.registrarComunicacaoValida(fonte.getId(), medicao.getRecebidoEm());
        return resposta(medicao);
    }

    @Transactional(readOnly = true)
    public MedicaoConsumoResponseDTO obter(Long id, Long medicaoId) {
        if (!medidores.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medidor não encontrado.");
        return resposta(medicoes.findByIdAndFonteAtivoId(medicaoId, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Medição não encontrada para este medidor.")));
    }

    private void validarInstalacao(MedidorInteligente medidor) {
        if (medidor.getDataInstalacao() == null || medidor.getConsumidor() == null || medidor.getTransformador() == null) {
            throw conflito("Medidor deve estar instalado, com consumidor, transformador e data de instalação.");
        }
    }

    private MedicaoConsumoResponseDTO resposta(MedicaoConsumoMedidor m) {
        return new MedicaoConsumoResponseDTO(m.getId(), m.getFonte().getAtivo().getId(), m.getFonte().getId(), m.getFonte().getOrigem(),
                utc(m.getInicioPeriodo()), utc(m.getFimPeriodo()), utc(m.getRecebidoEm()), m.getConsumoKwh(),
                m.getConsumidorId(), m.getTransformadorId(), m.getAlimentadorId(), m.getSubestacaoId());
    }
    private OffsetDateTime utc(OffsetDateTime instante) { return instante.withOffsetSameInstant(ZoneOffset.UTC); }
    private ResponseStatusException conflito(String mensagem) { return new ResponseStatusException(HttpStatus.CONFLICT, mensagem); }
    private ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
}
