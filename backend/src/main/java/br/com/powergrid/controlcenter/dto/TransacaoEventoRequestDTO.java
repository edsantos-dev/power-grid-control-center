package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransacaoEventoRequestDTO(
        @NotNull SituacaoEventoOperacional situacaoDestino,
        @NotNull SituacaoEventoOperacional situacaoEsperada,
        @NotBlank @Size(max = 1000) String comentario,
        OffsetDateTime terminoEfetivo, EstadoOperacional estadoAposReparo) {
    public TransacaoEventoRequestDTO(SituacaoEventoOperacional situacaoDestino,
            SituacaoEventoOperacional situacaoEsperada, String comentario) {
        this(situacaoDestino, situacaoEsperada, comentario, null, null);
    }
    public TransacaoEventoRequestDTO(SituacaoEventoOperacional destino, SituacaoEventoOperacional esperada,
            String comentario, OffsetDateTime terminoEfetivo) {
        this(destino, esperada, comentario, terminoEfetivo, null);
    }
}
