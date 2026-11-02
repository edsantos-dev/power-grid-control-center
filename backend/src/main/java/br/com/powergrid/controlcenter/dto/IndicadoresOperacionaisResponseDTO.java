package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import br.com.powergrid.controlcenter.model.TipoAtivoIndicador;
import br.com.powergrid.controlcenter.model.SituacaoDisponibilidade;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record IndicadoresOperacionaisResponseDTO(OffsetDateTime calculadoEm, OffsetDateTime dataInicio,
        OffsetDateTime dataFim, FiltrosAplicados filtrosAplicados, List<QuantidadeAtivos> quantidadeAtivos,
        long totalAtivos, long quantidadeIniciadasNoPeriodo, long quantidadeSobrepostasAoPeriodo,
        long quantidadeSemTerminoFisico, long quantidadeRestabelecimentosNoPeriodo, long quantidadeRegistrosInconsistentes,
        BigDecimal tempoInterrompidoSegundos, BigDecimal tempoMedioRestabelecimentoSegundos,
        BigDecimal tempoObservadoAtivosSegundos, BigDecimal disponibilidadeEstimadaPercentual,
        SituacaoDisponibilidade situacaoDisponibilidade) {

    public record FiltrosAplicados(Long ativoId, List<TipoAtivoIndicador> tipos, Long subestacaoId) { }
    public record QuantidadeAtivos(TipoAtivoIndicador tipoAtivo, EstadoOperacional estadoOperacional, long quantidade) { }
}
