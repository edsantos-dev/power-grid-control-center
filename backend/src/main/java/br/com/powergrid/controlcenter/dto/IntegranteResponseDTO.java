package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.PerfilAcesso;

public record IntegranteResponseDTO(Long id, Long equipeId, Long usuarioId, String nome,
        PerfilAcesso perfil, OffsetDateTime inicioVigencia, OffsetDateTime fimVigencia) {
}
