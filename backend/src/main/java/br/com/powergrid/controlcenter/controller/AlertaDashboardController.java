package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaDashboardResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaDashboardService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas-dashboard")
public class AlertaDashboardController {
    private final AlertaDashboardService service;

    public AlertaDashboardController(AlertaDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<AlertaDashboardResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
}
