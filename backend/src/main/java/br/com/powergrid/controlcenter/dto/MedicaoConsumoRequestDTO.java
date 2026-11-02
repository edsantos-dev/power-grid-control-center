package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;

public record MedicaoConsumoRequestDTO(@NotNull @Positive Long fonteId,
        @NotNull OffsetDateTime inicioPeriodo, @NotNull OffsetDateTime fimPeriodo,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal consumoKwh) {
    @AssertTrue(message = "Início do período deve ser anterior ao fim.")
    public boolean isPeriodoValido() {
        return inicioPeriodo == null || fimPeriodo == null || inicioPeriodo.toInstant().isBefore(fimPeriodo.toInstant());
    }
}
