package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaAtivoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaAtivoService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas/ativos")
public class AlertaAtivoController {
    private final AlertaAtivoService service;

    public AlertaAtivoController(AlertaAtivoService service) { this.service = service; }

    @GetMapping
    public PageResponseDTO<AlertaAtivoResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
}
