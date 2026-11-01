package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.SituacaoEquipe;
import jakarta.validation.constraints.NotNull;

public record SituacaoEquipeRequestDTO(@NotNull SituacaoEquipe situacao) {
}
