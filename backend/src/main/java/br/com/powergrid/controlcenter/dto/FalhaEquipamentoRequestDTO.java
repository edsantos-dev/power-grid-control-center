package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Criticidade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FalhaEquipamentoRequestDTO(@NotNull @Positive Long ativoId,
        @NotNull @Positive Long responsavelUsuarioId, @NotBlank String descricao, Criticidade criticidade) {
    public FalhaEquipamentoRequestDTO { if (descricao != null) descricao = descricao.trim(); }
}
