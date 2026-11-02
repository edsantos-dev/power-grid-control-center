package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InterrupcaoRequestDTO(
        @NotNull @Positive Long ativoId,
        @NotBlank String descricao,
        @NotNull @Positive Long responsavelUsuarioId,
        @NotNull OffsetDateTime inicioEfetivo,
        @Size(max = 32) String criticidade) {
    public InterrupcaoRequestDTO {
        if (descricao != null) descricao = descricao.trim();
    }
}
