package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;

public record EventoOperacionalResponseDTO(
        Long id, Long ativoId, Categoria categoria, String descricao,
        String criticidade, String situacao,
        Long registradorUsuarioId, Long responsavelUsuarioId,
        OffsetDateTime inicioPrevisto, OffsetDateTime terminoPrevisto,
        OffsetDateTime inicioEfetivo, OffsetDateTime terminoEfetivo) {
}
