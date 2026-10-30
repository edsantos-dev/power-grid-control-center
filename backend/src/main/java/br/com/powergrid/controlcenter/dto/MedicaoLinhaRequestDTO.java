package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MedicaoLinhaRequestDTO(
        @NotNull @Positive Long fonteId,
        @NotNull OffsetDateTime medidoEm,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal tensaoKv,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal correnteA,
        @Digits(integer = 13, fraction = 6) BigDecimal potenciaAtivaMw,
        @Digits(integer = 13, fraction = 6) BigDecimal potenciaReativaMvar,
        @Positive @Digits(integer = 3, fraction = 6) BigDecimal frequenciaHz) {
}
