package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

public record MedidorInstalacaoRequestDTO(
        @NotNull @Positive Long consumidorId,
        @NotNull @Positive Long transformadorId,
        @NotNull @DecimalMin("-90") @DecimalMax("90")
        @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @NotNull @PastOrPresent LocalDate dataInstalacao,
        String enderecoFornecimento) {
    public MedidorInstalacaoRequestDTO {
        if (enderecoFornecimento != null) {
            enderecoFornecimento = enderecoFornecimento.trim();
            if (enderecoFornecimento.isEmpty()) enderecoFornecimento = null;
        }
    }
}
