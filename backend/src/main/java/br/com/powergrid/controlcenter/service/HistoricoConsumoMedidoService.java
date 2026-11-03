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
import java.util.stream.Collectors;
import br.com.powergrid.controlcenter.dto.HistoricoConsumoMedidoResponseDTO;
import br.com.powergrid.controlcenter.dto.PontoConsumoMedidoDTO;
import br.com.powergrid.controlcenter.model.GranularidadeHistorico;
import br.com.powergrid.controlcenter.model.SituacaoConsumoMedido;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.MedicaoConsumoMedidorRepository;
import br.com.powergrid.controlcenter.repository.projection.ConsumoMedidoHistoricoProjection;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoConsumoMedidoService {
    private static final Set<String> PARAMETROS = Set.of("dataInicio", "dataFim", "granularidade");
    private final AlimentadorRepository alimentadores;
    private final MedicaoConsumoMedidorRepository medicoes;
    private final Clock clock;

    public HistoricoConsumoMedidoService(AlimentadorRepository alimentadores, MedicaoConsumoMedidorRepository medicoes, Clock clock) {
        this.alimentadores = alimentadores; this.medicoes = medicoes; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HistoricoConsumoMedidoResponseDTO consultar(Long id, MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
        var periodo = ParametrosConsultaMedicao.from(params);
        if (periodo.inicio() == null || periodo.fim() == null) throw invalido("dataInicio e dataFim são obrigatórios, com fuso.");
        if (periodo.fim().toInstant().isAfter(clock.instant())) throw invalido("dataFim não pode ser futura.");
        var granularidade = granularidade(params);
        var calendario = calendario(periodo.inicio(), periodo.fim(), granularidade);
        if (!alimentadores.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado.");
        var leituras = medicoes.buscarParaHistorico(id, periodo.inicio(), periodo.fim());
        var porMedidor = leituras.stream().collect(Collectors.groupingBy(ConsumoMedidoHistoricoProjection::medidorId));
        for (var grupo : porMedidor.values()) agregarMedidor(calendario, grupo);
        return new HistoricoConsumoMedidoResponseDTO(id, periodo.inicio(), periodo.fim(), granularidade, "UTC", !leituras.isEmpty(),
                porMedidor.size(), "NAO_AFERIDA", calendario.stream().map(p -> p.resposta(porMedidor.size())).toList());
    }

    private void agregarMedidor(List<PontoAgregado> calendario, List<ConsumoMedidoHistoricoProjection> leituras) {
        var estados = calendario.stream().map(p -> new CoberturaMedidor(p.inicio, p.fim)).toList();
        int indice = 0;
        for (var leitura : leituras) {
            var inicio = leitura.inicioPeriodo().toInstant(); var fim = leitura.fimPeriodo().toInstant();
            while (indice < estados.size() && !inicio.isBefore(estados.get(indice).fim)) indice++;
            if (indice == estados.size()) break;
            var estado = estados.get(indice);
            if (!inicio.isBefore(estado.inicio) && !fim.isAfter(estado.fim)) estado.adicionar(leitura);
            else {
                for (int j = indice; j < estados.size() && estados.get(j).inicio.isBefore(fim); j++) estados.get(j).inconsistente = true;
            }
        }
        for (int i = 0; i < estados.size(); i++) {
            var estado = estados.get(i); var ponto = calendario.get(i);
            ponto.leituras += estado.leituras;
            if (estado.completa()) {
                ponto.medidoresCompletos++;
                ponto.consumoKwh = ponto.consumoKwh.add(estado.consumoKwh);
            }
        }
    }

    private List<PontoAgregado> calendario(OffsetDateTime inicio, OffsetDateTime fim, GranularidadeHistorico granularidade) {
        var pontos = new ArrayList<PontoAgregado>(); var cursor = inicio;
        while (cursor.isBefore(fim)) {
            if (pontos.size() == 366) throw invalido("A consulta excede o limite de 366 pontos temporais.");
            var data = cursor.toLocalDate();
            var proxima = (granularidade == GranularidadeHistorico.DIA ? data.plusDays(1)
                    : data.withDayOfMonth(1).plusMonths(1)).atStartOfDay().atOffset(ZoneOffset.UTC);
            var limite = proxima.isAfter(fim) ? fim : proxima;
            pontos.add(new PontoAgregado(cursor.toInstant(), limite.toInstant())); cursor = limite;
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

    private static class CoberturaMedidor {
        private final Instant inicio;
        private final Instant fim;
        private Instant cursor;
        private BigDecimal consumoKwh = BigDecimal.ZERO;
        private long leituras;
        private boolean inconsistente;

        private CoberturaMedidor(Instant inicio, Instant fim) { this.inicio = inicio; this.fim = fim; this.cursor = inicio; }
        private void adicionar(ConsumoMedidoHistoricoProjection leitura) {
            if (!leitura.inicioPeriodo().toInstant().equals(cursor)) inconsistente = true;
            if (leitura.fimPeriodo().toInstant().isAfter(cursor)) cursor = leitura.fimPeriodo().toInstant();
            consumoKwh = consumoKwh.add(leitura.consumoKwh()); leituras++;
        }
        private boolean completa() { return !inconsistente && cursor.equals(fim); }
    }

    private static class PontoAgregado {
        private final Instant inicio;
        private final Instant fim;
        private BigDecimal consumoKwh = BigDecimal.ZERO;
        private long leituras;
        private long medidoresCompletos;

        private PontoAgregado(Instant inicio, Instant fim) { this.inicio = inicio; this.fim = fim; }
        private PontoConsumoMedidoDTO resposta(long populacao) {
            if (populacao == 0 || medidoresCompletos != populacao) {
                return new PontoConsumoMedidoDTO(inicio.atOffset(ZoneOffset.UTC), fim.atOffset(ZoneOffset.UTC), null,
                        leituras, medidoresCompletos, SituacaoConsumoMedido.DADOS_INSUFICIENTES);
            }
            var consumoMwh = consumoKwh.movePointLeft(3).setScale(9, RoundingMode.HALF_EVEN);
            return new PontoConsumoMedidoDTO(inicio.atOffset(ZoneOffset.UTC), fim.atOffset(ZoneOffset.UTC), consumoMwh,
                    leituras, medidoresCompletos, consumoKwh.signum() == 0 ? SituacaoConsumoMedido.SEM_CONSUMO : SituacaoConsumoMedido.OK);
        }
    }
}
