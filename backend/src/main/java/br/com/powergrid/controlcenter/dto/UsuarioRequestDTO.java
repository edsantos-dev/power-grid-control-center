package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import br.com.powergrid.controlcenter.validation.SenhaSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @SenhaSegura String senha,
        @NotNull PerfilAcesso perfil) {
    public UsuarioRequestDTO {
        nome = nome == null ? null : nome.trim();
        email = NormalizadorEmail.normalizar(email);
    }

    @Override
    public String toString() {
        return "UsuarioRequestDTO[perfil=" + perfil + "]";
    }
}
