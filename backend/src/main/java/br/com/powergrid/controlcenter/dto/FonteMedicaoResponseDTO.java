package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record FonteMedicaoResponseDTO(Long id, String identificador, Long linhaId, OrigemMedicao origem) {
}
