package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;

public record CoordenadaGeografica(BigDecimal latitude, BigDecimal longitude) {
    public CoordenadaGeografica {
        if (latitude == null || longitude == null || latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0 || longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Coordenadas geográficas ausentes ou fora dos limites.");
        }
    }
}
