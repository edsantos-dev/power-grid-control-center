package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;

public record AlimentadorParametroResponseDTO(Long alimentadorId, BigDecimal percentualPerdaTecnica) {
}
