package br.com.powergrid.controlcenter.controller;

import java.util.List;
import br.com.powergrid.controlcenter.dto.AlertaRequestDTO;
import br.com.powergrid.controlcenter.dto.AlertaResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {
    private final AlertaService service;

    public AlertaController(AlertaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AlertaResponseDTO> criar(@Valid @RequestBody AlertaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @GetMapping
    public List<AlertaResponseDTO> listarAtivos() {
        return service.listarAtivos();
    }
}
