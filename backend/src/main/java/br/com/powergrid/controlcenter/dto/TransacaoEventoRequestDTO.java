package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransacaoEventoRequestDTO(
        @NotNull SituacaoEventoOperacional situacaoDestino,
        @NotNull SituacaoEventoOperacional situacaoEsperada,
        @NotBlank @Size(max = 1000) String comentario) {
}
