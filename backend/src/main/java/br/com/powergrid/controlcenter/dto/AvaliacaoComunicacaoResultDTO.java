package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;

public record AvaliacaoComunicacaoResultDTO(OffsetDateTime avaliadoEm, int toleranciaSegundos,
        long fontesAvaliadas, long alertasCriados, long alertasAtivosExistentes) {
}
