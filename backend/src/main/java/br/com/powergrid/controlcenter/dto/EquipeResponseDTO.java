package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.SituacaoEquipe;

public record EquipeResponseDTO(Long id, String nome, String responsabilidades,
        String especialidades, SituacaoEquipe situacao) {
}
