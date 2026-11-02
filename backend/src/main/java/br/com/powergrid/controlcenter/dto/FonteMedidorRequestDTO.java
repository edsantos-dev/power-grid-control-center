package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.OrigemMedicao;
import jakarta.validation.constraints.NotNull;

public record FonteMedidorRequestDTO(@NotNull OrigemMedicao origem) { }
