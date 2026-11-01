package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.TipoGeracao;
import jakarta.validation.constraints.*;

public record UsinaRequestDTO(
        @NotBlank @Size(max = 255) String nome,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @NotNull TipoGeracao tipoGeracao,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal capacidadeMaximaMw,
        @NotNull @Positive Long subestacaoId,
        LocalDate dataInstalacao,
        EstadoOperacional estadoOperacional) {
    public UsinaRequestDTO {
        if (nome != null) nome = nome.trim();
    }
}
