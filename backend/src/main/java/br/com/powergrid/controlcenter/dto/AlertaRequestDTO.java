package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AlertaRequestDTO(
        @NotBlank @Size(max = 128) String tipo,
        @NotNull Criticidade criticidade,
        @NotNull EscopoAlerta escopo,
        @Positive Long ativoId) {

    public AlertaRequestDTO {
        if (tipo != null) tipo = tipo.trim();
    }
}
