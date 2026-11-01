package br.com.powergrid.controlcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipeRequestDTO(@NotBlank @Size(max = 255) String nome,
        String responsabilidades, String especialidades) {
    public EquipeRequestDTO {
        if (nome != null) nome = nome.trim();
    }
}
