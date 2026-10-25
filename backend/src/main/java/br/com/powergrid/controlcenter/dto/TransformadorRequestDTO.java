package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransformadorRequestDTO(
		@NotBlank @Size(max = 255) String nome,
		@NotNull @DecimalMin("-90") @DecimalMax("90")
		@Digits(integer = 3, fraction = 7) BigDecimal latitude,
		@NotNull @DecimalMin("-180") @DecimalMax("180")
		@Digits(integer = 3, fraction = 7) BigDecimal longitude,
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal capacidadeMva,
		@NotNull @Positive Long alimentadorId,
		@NotNull LocalDate dataInstalacao,
		EstadoOperacional estadoOperacional) {

	public TransformadorRequestDTO {
		if (nome != null) {
			nome = nome.trim();
		}
	}

}
