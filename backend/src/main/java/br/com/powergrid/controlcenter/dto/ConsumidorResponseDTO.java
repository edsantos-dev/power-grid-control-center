package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;

public record ConsumidorResponseDTO(Long id, String nomeRazaoSocial, TipoPessoa tipoPessoa,
        String documento, String enderecoCorrespondencia) {
}
