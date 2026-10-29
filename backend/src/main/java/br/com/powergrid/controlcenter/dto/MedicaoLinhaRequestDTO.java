package br.com.powergrid.controlcenter.dto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;
public record MedicaoLinhaRequestDTO(
        @NotNull @Positive Long fonteId,
        @NotNull OffsetDateTime medidoEm,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal tensaoKv,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal correnteA) {
}
