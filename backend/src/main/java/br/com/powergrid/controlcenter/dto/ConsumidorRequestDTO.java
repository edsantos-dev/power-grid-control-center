package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsumidorRequestDTO(
        @NotBlank @Size(max = 255) String nomeRazaoSocial,
        @NotNull TipoPessoa tipoPessoa,
        @NotBlank String documento,
        String enderecoCorrespondencia) {
    public ConsumidorRequestDTO {
        if (nomeRazaoSocial != null) nomeRazaoSocial = nomeRazaoSocial.trim();
        if (enderecoCorrespondencia != null) {
            enderecoCorrespondencia = enderecoCorrespondencia.trim();
            if (enderecoCorrespondencia.isEmpty()) enderecoCorrespondencia = null;
        }
    }
}
