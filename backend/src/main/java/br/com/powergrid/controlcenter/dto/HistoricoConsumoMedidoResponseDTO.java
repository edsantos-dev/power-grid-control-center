package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import java.util.List;
import br.com.powergrid.controlcenter.model.GranularidadeHistorico;

public record HistoricoConsumoMedidoResponseDTO(Long alimentadorId, OffsetDateTime dataInicio, OffsetDateTime dataFim,
        GranularidadeHistorico granularidade, String fusoHorario, boolean possuiDados,
        long quantidadeMedidoresConsiderados, String coberturaCadastral, List<PontoConsumoMedidoDTO> pontos) {
    public HistoricoConsumoMedidoResponseDTO { pontos = List.copyOf(pontos); }
}
