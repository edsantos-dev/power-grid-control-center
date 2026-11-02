package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.HistoricoEnergiaResponseDTO;
import br.com.powergrid.controlcenter.service.HistoricoEnergiaService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alimentadores/{id}/historico-energia")
public class HistoricoEnergiaController {
    private final HistoricoEnergiaService service;

    public HistoricoEnergiaController(HistoricoEnergiaService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public HistoricoEnergiaResponseDTO consultar(@PathVariable @Positive Long id, @RequestParam MultiValueMap<String, String> params) {
        return service.consultar(id, params);
    }
}
