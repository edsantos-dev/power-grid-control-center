package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.MedicaoEnergiaAlimentador;
import br.com.powergrid.controlcenter.model.SituacaoPerdaTecnica;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.MedicaoEnergiaAlimentadorRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerdaTecnicaService {
    private final AlimentadorRepository alimentadores;
    private final MedicaoEnergiaAlimentadorRepository medicoes;

    public PerdaTecnicaService(AlimentadorRepository alimentadores, MedicaoEnergiaAlimentadorRepository medicoes) {
        this.alimentadores = alimentadores; this.medicoes = medicoes;
    }

    @Transactional(readOnly = true)
    public PerdaTecnicaResponseDTO calcular(Long id, MultiValueMap<String, String> params) {
        validarParametros(params, Set.of("dataInicio", "dataFim"));
        var consulta = ParametrosConsultaMedicao.from(params, "inicioPeriodo");
        if (consulta.inicio() == null || consulta.fim() == null) throw invalido("dataInicio e dataFim são obrigatórios, com fuso.");
        validarAlimentador(id);
        var leituras = medicoes.buscarParaCalculo(id, consulta.inicio(), consulta.fim());
        var cursor = consulta.inicio().toInstant();
        BigDecimal cobertura = BigDecimal.ZERO;
        BigDecimal energia = BigDecimal.ZERO;
        BigDecimal perda = BigDecimal.ZERO;
        boolean sobreposicao = false;
        for (var leitura : leituras) {
            Instant inicio = leitura.getInicioPeriodo().toInstant(), fim = leitura.getFimPeriodo().toInstant();
            if (inicio.isBefore(cursor)) sobreposicao = true;
            Instant inicioUtil = inicio.isBefore(cursor) ? cursor : inicio;
            if (fim.isAfter(inicioUtil)) cobertura = cobertura.add(segundos(inicioUtil, fim));
            if (fim.isAfter(cursor)) cursor = fim;
            energia = energia.add(leitura.getEnergiaInjetadaMwh());
            perda = perda.add(leitura.getEnergiaInjetadaMwh().multiply(leitura.getPercentualPerdaReferencia()).movePointLeft(2));
        }
        BigDecimal duracao = segundos(consulta.inicio().toInstant(), consulta.fim().toInstant());
        BigDecimal percentualCobertura = cobertura.multiply(new BigDecimal("100")).divide(duracao, 6, RoundingMode.HALF_EVEN);
        var detalhes = leituras.stream().map(this::resposta).toList();
        if (sobreposicao || cobertura.compareTo(duracao) != 0) {
            return new PerdaTecnicaResponseDTO(id, consulta.inicio(), consulta.fim(), null, null, null,
                    SituacaoPerdaTecnica.DADOS_INSUFICIENTES, percentualCobertura, detalhes);
        }
        BigDecimal percentual = energia.signum() == 0 ? null : perda.multiply(new BigDecimal("100")).divide(energia, 6, RoundingMode.HALF_EVEN);
        return new PerdaTecnicaResponseDTO(id, consulta.inicio(), consulta.fim(), energia.setScale(6, RoundingMode.HALF_EVEN),
                perda.setScale(6, RoundingMode.HALF_EVEN), percentual,
                energia.signum() == 0 ? SituacaoPerdaTecnica.SEM_ENERGIA_INJETADA : SituacaoPerdaTecnica.OK,
                percentualCobertura, detalhes);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MedicaoEnergiaResponseDTO> listarMedicoes(Long id, MultiValueMap<String, String> params) {
        validarParametros(params, Set.of("page", "size", "sort", "dataInicio", "dataFim"));
        var consulta = ParametrosConsultaMedicao.from(params, "inicioPeriodo");
        validarAlimentador(id);
        return PageResponseDTO.from(medicoes.buscarPorAlimentadorEPeriodo(id, consulta.inicio(), consulta.fim(), consulta.pageable()).map(this::resposta));
    }

    private MedicaoEnergiaResponseDTO resposta(MedicaoEnergiaAlimentador m) {
        var fonte = m.getFonte();
        return new MedicaoEnergiaResponseDTO(m.getId(), fonte.getAtivo().getId(), fonte.getId(), fonte.getOrigem(),
                utc(m.getInicioPeriodo()), utc(m.getFimPeriodo()), utc(m.getRecebidoEm()), m.getEnergiaInjetadaMwh(), m.getPercentualPerdaReferencia());
    }

    private BigDecimal segundos(Instant inicio, Instant fim) {
        var duracao = Duration.between(inicio, fim);
        return BigDecimal.valueOf(duracao.getSeconds()).add(BigDecimal.valueOf(duracao.getNano(), 9));
    }

    private OffsetDateTime utc(OffsetDateTime instante) { return instante.withOffsetSameInstant(ZoneOffset.UTC); }

    private void validarAlimentador(Long id) {
        if (!alimentadores.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado.");
    }

    private void validarParametros(MultiValueMap<String, String> params, Set<String> permitidos) {
        for (String nome : params.keySet()) if (!permitidos.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
    }

    private ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
}
