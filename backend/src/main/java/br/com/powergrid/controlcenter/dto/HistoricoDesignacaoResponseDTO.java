package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;

public record HistoricoDesignacaoResponseDTO(Long id, Long eventoOperacionalId,
        Long equipeAnteriorId, Long equipeNovaId, Long usuarioId, OffsetDateTime registradoEm,
        String comentario, Integer versaoDesignacao) { }
