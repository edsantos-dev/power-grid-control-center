package br.com.powergrid.controlcenter.dto;

import java.time.Instant;

public record AmostraSnapshotDTO<T>(T valor, Instant medidoEm, Instant recebidoEm, Long fonteId) {
}
