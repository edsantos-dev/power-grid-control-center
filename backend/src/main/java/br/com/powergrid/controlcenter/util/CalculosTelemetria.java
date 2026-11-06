package br.com.powergrid.controlcenter.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import br.com.powergrid.controlcenter.model.SituacaoFatorPotencia;

public final class CalculosTelemetria {
    private CalculosTelemetria() {
    }

    public static FatorPotencia fatorPotencia(BigDecimal ativa, BigDecimal reativa) {
        if (ativa == null || reativa == null) {
            return new FatorPotencia(null, SituacaoFatorPotencia.DADOS_INSUFICIENTES);
        }
        var quadrado = ativa.multiply(ativa).add(reativa.multiply(reativa));
        if (quadrado.signum() == 0) {
            return new FatorPotencia(null, SituacaoFatorPotencia.POTENCIA_APARENTE_NULA);
        }
        var valor = ativa.abs().divide(quadrado.sqrt(MathContext.DECIMAL128), 6, RoundingMode.HALF_EVEN);
        return new FatorPotencia(valor, SituacaoFatorPotencia.CALCULADO);
    }

    public static BigDecimal cargaPercentual(BigDecimal potencia, BigDecimal capacidade) {
        return potencia.movePointRight(2).divide(capacidade, 6, RoundingMode.HALF_EVEN);
    }

    public record FatorPotencia(BigDecimal valor, SituacaoFatorPotencia situacao) {
    }
}
