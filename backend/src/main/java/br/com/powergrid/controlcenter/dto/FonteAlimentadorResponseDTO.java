package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record FonteAlimentadorResponseDTO(Long id, String identificador, Long alimentadorId, OrigemMedicao origem) {
}
