package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaComunicacaoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaComunicacaoService;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas-comunicacao")
public class AlertaComunicacaoController {
    private final AlertaComunicacaoService service;

    public AlertaComunicacaoController(AlertaComunicacaoService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<AlertaComunicacaoResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }

    @GetMapping("/{id}")
    public AlertaComunicacaoResponseDTO buscarPorId(@PathVariable @Positive Long id) {
        return service.buscarPorId(id);
    }
}
