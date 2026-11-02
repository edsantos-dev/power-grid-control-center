package br.com.powergrid.controlcenter.dto.geojson;

public sealed interface GeoJsonGeometry permits GeoJsonPoint, GeoJsonLineString {
    String type();
}
