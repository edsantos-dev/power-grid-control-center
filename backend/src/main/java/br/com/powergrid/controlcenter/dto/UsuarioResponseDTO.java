package br.com.powergrid.controlcenter.dto;

import java.time.Instant;

import br.com.powergrid.controlcenter.model.PerfilAcesso;

public record UsuarioResponseDTO(Long id, String nome, String email, String matricula, PerfilAcesso perfil,
        Long criadoPorUsuarioId, Instant criadoEm) {
}
