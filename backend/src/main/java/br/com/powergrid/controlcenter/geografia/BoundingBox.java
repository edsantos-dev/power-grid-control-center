package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;

public record BoundingBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    public BoundingBox {
        new CoordenadaGeografica(minLat, minLng);
        new CoordenadaGeografica(maxLat, maxLng);
        if (minLat.compareTo(maxLat) >= 0 || minLng.compareTo(maxLng) >= 0) {
            throw new IllegalArgumentException("Limites mínimos devem ser inferiores aos máximos, sem cruzar o antimeridiano.");
        }
    }

    public boolean contem(CoordenadaGeografica ponto) {
        return ponto.latitude().compareTo(minLat) >= 0 && ponto.latitude().compareTo(maxLat) <= 0
                && ponto.longitude().compareTo(minLng) >= 0 && ponto.longitude().compareTo(maxLng) <= 0;
    }
}
