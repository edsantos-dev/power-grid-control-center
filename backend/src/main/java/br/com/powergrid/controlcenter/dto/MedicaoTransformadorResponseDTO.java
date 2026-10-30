package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record MedicaoTransformadorResponseDTO(Long id, Long transformadorId, Long fonteId, OrigemMedicao origem,
        OffsetDateTime medidoEm, OffsetDateTime recebidoEm, BigDecimal potenciaAparenteMva,
        BigDecimal capacidadeReferenciaMva, BigDecimal cargaPercentual) {
}
