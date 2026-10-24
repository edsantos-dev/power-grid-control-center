package br.com.powergrid.controlcenter.dto;

import br.com.powergrid.controlcenter.model.PerfilAcesso;

public record UsuarioAutenticadoResponse(Long id, String nome, String email, PerfilAcesso perfil) {

}
