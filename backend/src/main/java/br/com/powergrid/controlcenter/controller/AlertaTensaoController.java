package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaTensaoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaTensaoService;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas-tensao")
public class AlertaTensaoController {
    private final AlertaTensaoService service;
    public AlertaTensaoController(AlertaTensaoService service) { this.service = service; }
    @GetMapping
    public PageResponseDTO<AlertaTensaoResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
    @GetMapping("/{id}")
    public AlertaTensaoResponseDTO buscarPorId(@PathVariable @Positive Long id) { return service.buscarPorId(id); }
}
