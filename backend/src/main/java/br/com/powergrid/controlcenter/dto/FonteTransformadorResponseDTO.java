package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record FonteTransformadorResponseDTO(Long id, String identificador, Long transformadorId, OrigemMedicao origem) {
}
