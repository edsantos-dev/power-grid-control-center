package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import java.util.List;
import br.com.powergrid.controlcenter.model.GranularidadeHistorico;

public record HistoricoEnergiaResponseDTO(Long alimentadorId, OffsetDateTime dataInicio, OffsetDateTime dataFim,
        GranularidadeHistorico granularidade, String fusoHorario, boolean possuiDados, List<PontoHistoricoDTO> pontos) {
    public HistoricoEnergiaResponseDTO { pontos = List.copyOf(pontos); }
}
