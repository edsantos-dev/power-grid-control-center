package br.com.powergrid.controlcenter.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record MonitoramentoSnapshotResponseDTO(Instant consultadoEm,
        @JsonUnwrapped PageResponseDTO<AtivoSnapshotDTO> pagina) {
}
