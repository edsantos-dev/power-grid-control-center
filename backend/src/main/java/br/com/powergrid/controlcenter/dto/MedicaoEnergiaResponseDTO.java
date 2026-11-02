package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record MedicaoEnergiaResponseDTO(Long id, Long alimentadorId, Long fonteId, OrigemMedicao origem,
        OffsetDateTime inicioPeriodo, OffsetDateTime fimPeriodo, OffsetDateTime recebidoEm,
        BigDecimal energiaInjetadaMwh, BigDecimal percentualPerdaReferencia) {
}
