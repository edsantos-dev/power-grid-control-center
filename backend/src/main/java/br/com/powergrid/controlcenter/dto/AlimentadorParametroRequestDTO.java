package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record AlimentadorParametroRequestDTO(
        @NotNull @DecimalMin("0") @DecimalMax(value = "100", inclusive = false)
        @Digits(integer = 3, fraction = 6) BigDecimal percentualPerdaTecnica) {
}
