package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;

public record ConsumidorResumoResponseDTO(Long id, String nomeRazaoSocial, TipoPessoa tipoPessoa) {
}
