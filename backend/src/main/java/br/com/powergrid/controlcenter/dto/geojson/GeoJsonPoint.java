package br.com.powergrid.controlcenter.dto.geojson;

import java.math.BigDecimal;
import java.util.List;

public record GeoJsonPoint(String type, List<BigDecimal> coordinates) implements GeoJsonGeometry {
    public GeoJsonPoint(BigDecimal longitude, BigDecimal latitude) { this("Point", List.of(longitude, latitude)); }
}
