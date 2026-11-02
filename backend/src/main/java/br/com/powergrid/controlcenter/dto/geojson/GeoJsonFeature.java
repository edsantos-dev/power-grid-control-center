package br.com.powergrid.controlcenter.dto.geojson;

public record GeoJsonFeature(String type, String id, GeoJsonGeometry geometry, GeoJsonProperties properties) {
    public GeoJsonFeature(GeoJsonGeometry geometry, GeoJsonProperties properties) {
        this("Feature", properties.ativoId().toString(), geometry, properties);
    }
}
