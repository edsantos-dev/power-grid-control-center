package br.com.powergrid.controlcenter.dto.geojson;

import java.util.List;

public record GeoJsonFeatureCollection(String type, List<GeoJsonFeature> features, MapaMetadados metadados) {
    public GeoJsonFeatureCollection(List<GeoJsonFeature> features, long semGeometria) {
        this("FeatureCollection", List.copyOf(features), new MapaMetadados(features.size(), semGeometria));
    }
}
