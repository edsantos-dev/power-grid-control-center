package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.geojson.GeoJsonFeatureCollection;
import br.com.powergrid.controlcenter.service.MapaGeograficoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mapa/ativos")
public class MapaGeograficoController {
    private final MapaGeograficoService service;

    public MapaGeograficoController(MapaGeograficoService service) { this.service = service; }

    @GetMapping(produces = "application/geo+json")
    @PreAuthorize("hasAnyRole('OPERADOR', 'GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public GeoJsonFeatureCollection consultar(@RequestParam MultiValueMap<String, String> params) {
        return service.consultar(params);
    }
}
