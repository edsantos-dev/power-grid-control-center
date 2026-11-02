package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import br.com.powergrid.controlcenter.model.SituacaoPerdaTecnica;

public record PerdaTecnicaResponseDTO(Long alimentadorId, OffsetDateTime dataInicio, OffsetDateTime dataFim,
        BigDecimal totalEnergiaInjetadaMwh, BigDecimal totalPerdaEstimadaMwh, BigDecimal percentualPonderado,
        SituacaoPerdaTecnica situacao, BigDecimal percentualCobertura,
        List<MedicaoEnergiaResponseDTO> medicoesConsideradas) {
}
