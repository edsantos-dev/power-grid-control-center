package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedidorRequestDTO(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Size(max = 128) String numeroSerie,
        EstadoOperacional estadoOperacional) {
    public MedidorRequestDTO {
        if (nome != null) nome = nome.trim();
        if (numeroSerie != null) numeroSerie = numeroSerie.trim();
    }
}
