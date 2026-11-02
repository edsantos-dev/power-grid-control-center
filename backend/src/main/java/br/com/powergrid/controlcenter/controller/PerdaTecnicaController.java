package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.PerdaTecnicaService;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alimentadores/{id}")
public class PerdaTecnicaController {
    private final PerdaTecnicaService service;

    public PerdaTecnicaController(PerdaTecnicaService service) { this.service = service; }

    @GetMapping("/perdas-tecnicas")
    public PerdaTecnicaResponseDTO calcular(@PathVariable @Positive Long id, @RequestParam MultiValueMap<String, String> params) {
        return service.calcular(id, params);
    }

    @GetMapping("/medicoes-energia")
    public PageResponseDTO<MedicaoEnergiaResponseDTO> listar(@PathVariable @Positive Long id, @RequestParam MultiValueMap<String, String> params) {
        return service.listarMedicoes(id, params);
    }
}
