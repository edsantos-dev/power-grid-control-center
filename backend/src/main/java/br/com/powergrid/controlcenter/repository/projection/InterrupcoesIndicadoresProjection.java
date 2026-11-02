package br.com.powergrid.controlcenter.repository.projection;

public record InterrupcoesIndicadoresProjection(long iniciadasNoPeriodo, long sobrepostasAoPeriodo,
        long semTerminoFisico, long registrosInconsistentes) { }
