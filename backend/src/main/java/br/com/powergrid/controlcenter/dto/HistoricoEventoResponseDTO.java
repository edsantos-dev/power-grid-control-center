package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;

public record HistoricoEventoResponseDTO(Long id, Long eventoId, Long usuarioId,
        SituacaoEventoOperacional situacaoAnterior, SituacaoEventoOperacional situacaoNova,
        OffsetDateTime alteradoEm, String comentario) {
}
