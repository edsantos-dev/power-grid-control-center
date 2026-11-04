package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record FalhaEquipamentoResponseDTO(Long id, EventoOperacionalResponseDTO evento,
        EstadoOperacional estadoAnterior, OffsetDateTime registradoEm,
        EstadoOperacional estadoAposReparo, EstadoOperacional estadoOperacionalAtual) { }
