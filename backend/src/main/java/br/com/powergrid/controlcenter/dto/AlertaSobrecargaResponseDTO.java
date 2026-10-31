package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.StatusAlerta;

public record AlertaSobrecargaResponseDTO(Long id, Long ativoId, Long medicaoId,
        BigDecimal cargaPercentual, BigDecimal limiarPercentual, StatusAlerta status,
        OffsetDateTime geradoEm, OffsetDateTime reconhecidoEm, Long reconhecidoPorUsuarioId) {
}
