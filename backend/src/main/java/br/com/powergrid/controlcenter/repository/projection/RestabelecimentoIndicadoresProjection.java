package br.com.powergrid.controlcenter.repository.projection;

import java.math.BigDecimal;

public record RestabelecimentoIndicadoresProjection(long interrupcoesConcluidas,
        BigDecimal duracaoTotalSegundos, BigDecimal tempoMedioSegundos) { }
