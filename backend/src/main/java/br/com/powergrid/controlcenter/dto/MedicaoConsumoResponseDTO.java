package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record MedicaoConsumoResponseDTO(Long id, Long medidorId, Long fonteId, OrigemMedicao origem,
        OffsetDateTime inicioPeriodo, OffsetDateTime fimPeriodo, OffsetDateTime recebidoEm, BigDecimal consumoKwh,
        Long consumidorId, Long transformadorId, Long alimentadorId, Long subestacaoId) { }
