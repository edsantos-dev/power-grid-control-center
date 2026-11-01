package br.com.powergrid.controlcenter.dto;

public record ManutencaoHistoricoResponseDTO(EventoOperacionalResponseDTO evento,
        HistoricoEventoResponseDTO ultimaTransicao) {
}
