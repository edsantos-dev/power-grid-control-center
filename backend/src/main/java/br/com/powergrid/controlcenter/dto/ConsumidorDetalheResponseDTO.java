package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;

public record ConsumidorDetalheResponseDTO(Long id, String nomeRazaoSocial, TipoPessoa tipoPessoa,
        String documento, String enderecoCompleto) {
    public ConsumidorResumoResponseDTO resumo() {
        return new ConsumidorResumoResponseDTO(id, nomeRazaoSocial, tipoPessoa);
    }

    @Override
    public String toString() {
        return "ConsumidorDetalheResponseDTO[id=" + id + ", tipoPessoa=" + tipoPessoa + "]";
    }
}
