package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.IndicadoresOperacionaisResponseDTO;
import br.com.powergrid.controlcenter.service.IndicadoresService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/indicadores/operacionais")
public class IndicadoresController {
    private final IndicadoresService service;

    public IndicadoresController(IndicadoresService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public IndicadoresOperacionaisResponseDTO consultar(@RequestParam MultiValueMap<String, String> params) {
        return service.calcular(params);
    }
}
