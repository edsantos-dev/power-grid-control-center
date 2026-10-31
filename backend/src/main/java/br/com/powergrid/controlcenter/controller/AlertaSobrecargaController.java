package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaSobrecargaResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaSobrecargaService;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas-sobrecarga")
public class AlertaSobrecargaController {
    private final AlertaSobrecargaService service;
    public AlertaSobrecargaController(AlertaSobrecargaService service) { this.service = service; }

    @GetMapping
    public PageResponseDTO<AlertaSobrecargaResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
    @GetMapping("/{id}")
    public AlertaSobrecargaResponseDTO buscarPorId(@PathVariable @Positive Long id) {
        return service.buscarPorId(id);
    }
}
