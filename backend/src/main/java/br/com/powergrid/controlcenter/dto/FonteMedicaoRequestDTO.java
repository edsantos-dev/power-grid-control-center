package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.OrigemMedicao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record FonteMedicaoRequestDTO(@NotBlank @Size(max = 128) String identificador,
        @NotNull OrigemMedicao origem, @Positive Long subestacaoReferenciaId) {
    public FonteMedicaoRequestDTO {
        if (identificador != null) identificador = identificador.trim();
    }
}
