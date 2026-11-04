package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;

public record HistoricoAlertaResponseDTO(Long id, Long alertaId, Long usuarioId,
        SituacaoAlerta situacaoAnterior, SituacaoAlerta situacaoNova,
        OffsetDateTime alteradoEm, String comentario) { }
