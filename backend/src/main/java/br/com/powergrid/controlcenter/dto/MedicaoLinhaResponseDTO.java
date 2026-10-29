package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record MedicaoLinhaResponseDTO(Long id, Long linhaId, Long fonteId, OrigemMedicao origem,
        OffsetDateTime medidoEm, OffsetDateTime recebidoEm, BigDecimal tensaoKv, BigDecimal correnteA) {
}
