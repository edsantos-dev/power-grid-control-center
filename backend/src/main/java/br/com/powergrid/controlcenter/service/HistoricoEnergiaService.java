package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.HistoricoEnergiaResponseDTO;
import br.com.powergrid.controlcenter.dto.PontoHistoricoDTO;
import br.com.powergrid.controlcenter.model.GranularidadeHistorico;
import br.com.powergrid.controlcenter.model.SituacaoPerdaTecnica;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.MedicaoEnergiaAlimentadorRepository;
import br.com.powergrid.controlcenter.repository.projection.MedicaoEnergiaHistoricoProjection;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoEnergiaService {
    private static final Set<String> PARAMETROS = Set.of("dataInicio", "dataFim", "granularidade");
    private final AlimentadorRepository alimentadores;
    private final MedicaoEnergiaAlimentadorRepository medicoes;
    private final Clock clock;

    public HistoricoEnergiaService(AlimentadorRepository alimentadores, MedicaoEnergiaAlimentadorRepository medicoes, Clock clock) {
        this.alimentadores = alimentadores; this.medicoes = medicoes; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HistoricoEnergiaResponseDTO consultar(Long id, MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
        var periodo = ParametrosConsultaMedicao.from(params);
        if (periodo.inicio() == null || periodo.fim() == null) throw invalido("dataInicio e dataFim são obrigatórios, com fuso.");
        if (periodo.fim().toInstant().isAfter(clock.instant())) throw invalido("dataFim não pode ser futura.");
        var granularidade = granularidade(params);
        var calendario = calendario(periodo.inicio(), periodo.fim(), granularidade);
        if (!alimentadores.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado.");
        var leituras = medicoes.buscarParaHistorico(id, periodo.inicio(), periodo.fim());
        int indice = 0;
        for (var leitura : leituras) {
            var inicio = leitura.inicioPeriodo().toInstant();
            var fim = leitura.fimPeriodo().toInstant();
            while (indice < calendario.size() && !inicio.isBefore(calendario.get(indice).fim)) indice++;
            if (indice == calendario.size()) break;
            var ponto = calendario.get(indice);
            if (!inicio.isBefore(ponto.inicio) && !fim.isAfter(ponto.fim)) ponto.adicionar(leitura);
            else {
                // Não ratear: todos os pontos atravessados ficam insuficientes, inclusive em sobreposição legada.
                for (int j = indice; j < calendario.size() && calendario.get(j).inicio.isBefore(fim); j++) {
                    calendario.get(j).inconsistente = true;
                }
            }
        }
        return new HistoricoEnergiaResponseDTO(id, periodo.inicio(), periodo.fim(), granularidade, "UTC", !leituras.isEmpty(),
                calendario.stream().map(Ponto::resposta).toList());
    }

    private List<Ponto> calendario(OffsetDateTime inicio, OffsetDateTime fim, GranularidadeHistorico granularidade) {
        var pontos = new ArrayList<Ponto>();
        var cursor = inicio;
        while (cursor.isBefore(fim)) {
            if (pontos.size() == 366) throw invalido("A consulta excede o limite de 366 pontos temporais.");
            var data = cursor.toLocalDate();
            var proxima = (granularidade == GranularidadeHistorico.DIA ? data.plusDays(1)
                    : data.withDayOfMonth(1).plusMonths(1)).atStartOfDay().atOffset(ZoneOffset.UTC);
            var limite = proxima.isAfter(fim) ? fim : proxima;
            pontos.add(new Ponto(cursor.toInstant(), limite.toInstant()));
            cursor = limite;
        }
        return pontos;
    }

    private GranularidadeHistorico granularidade(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "granularidade");
        if (valor == null) throw invalido("granularidade é obrigatória: DIA ou MES.");
        try { return GranularidadeHistorico.valueOf(valor); }
        catch (IllegalArgumentException ex) { throw invalido("granularidade deve ser DIA ou MES."); }
    }

    private ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }

    private static class Ponto {
        private final Instant inicio;
        private final Instant fim;
        private Instant cursor;
        private BigDecimal energia = BigDecimal.ZERO;
        private BigDecimal perda = BigDecimal.ZERO;
        private long quantidade;
        private boolean inconsistente;

        private Ponto(Instant inicio, Instant fim) { this.inicio = inicio; this.fim = fim; this.cursor = inicio; }

        private void adicionar(MedicaoEnergiaHistoricoProjection leitura) {
            var inicioLeitura = leitura.inicioPeriodo().toInstant();
            var fimLeitura = leitura.fimPeriodo().toInstant();
            if (!inicioLeitura.equals(cursor)) inconsistente = true;
            if (fimLeitura.isAfter(cursor)) cursor = fimLeitura;
            quantidade++;
            energia = energia.add(leitura.energiaInjetadaMwh());
            perda = perda.add(leitura.energiaInjetadaMwh().multiply(leitura.percentualPerdaReferencia()).movePointLeft(2));
        }

        private PontoHistoricoDTO resposta() {
            var inicioUtc = inicio.atOffset(ZoneOffset.UTC); var fimUtc = fim.atOffset(ZoneOffset.UTC);
            if (inconsistente || !cursor.equals(fim)) {
                return new PontoHistoricoDTO(inicioUtc, fimUtc, null, null, null, quantidade, SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
            }
            var percentual = energia.signum() == 0 ? null : perda.multiply(new BigDecimal("100")).divide(energia, 6, RoundingMode.HALF_EVEN);
            return new PontoHistoricoDTO(inicioUtc, fimUtc, energia.setScale(6, RoundingMode.HALF_EVEN), perda.setScale(6, RoundingMode.HALF_EVEN),
                    percentual, quantidade, energia.signum() == 0 ? SituacaoPerdaTecnica.SEM_ENERGIA_INJETADA : SituacaoPerdaTecnica.OK);
        }
    }
}
