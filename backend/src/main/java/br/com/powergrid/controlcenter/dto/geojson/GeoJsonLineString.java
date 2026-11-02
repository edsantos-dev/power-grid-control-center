package br.com.powergrid.controlcenter.dto.geojson;

import java.math.BigDecimal;
import java.util.List;

public record GeoJsonLineString(String type, List<List<BigDecimal>> coordinates) implements GeoJsonGeometry {
    public GeoJsonLineString(BigDecimal longitudeOrigem, BigDecimal latitudeOrigem, BigDecimal longitudeDestino, BigDecimal latitudeDestino) {
        this("LineString", List.of(List.of(longitudeOrigem, latitudeOrigem), List.of(longitudeDestino, latitudeDestino)));
    }
}
