package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EventoOperacionalRequestDTO(
        @NotNull @Positive Long ativoId,
        @NotNull Categoria categoria,
        @NotBlank String descricao,
        @NotNull @Positive Long responsavelUsuarioId,
        @NotNull OffsetDateTime inicioPrevisto,
        OffsetDateTime terminoPrevisto,
        @Size(max = 32) String criticidade) {

    public EventoOperacionalRequestDTO {
        if (descricao != null) descricao = descricao.trim();
    }

    @AssertTrue(message = "Categoria deve ser MANOBRA ou DESLIGAMENTO_PROGRAMADO.")
    public boolean isCategoriaPermitida() {
        return categoria == null || categoria == Categoria.MANOBRA
                || categoria == Categoria.DESLIGAMENTO_PROGRAMADO;
    }

    @AssertTrue(message = "Término previsto não pode anteceder o início previsto.")
    public boolean isPeriodoValido() {
        return inicioPrevisto == null || terminoPrevisto == null
                || !terminoPrevisto.toInstant().isBefore(inicioPrevisto.toInstant());
    }
}
