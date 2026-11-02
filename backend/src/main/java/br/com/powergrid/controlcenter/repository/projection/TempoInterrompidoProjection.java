package br.com.powergrid.controlcenter.repository.projection;

import java.math.BigDecimal;

public record TempoInterrompidoProjection(long ativosComInterrupcao,
        long intervalosUnificados, BigDecimal totalSegundos) { }
