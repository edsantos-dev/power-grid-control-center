package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.FalhaEquipamentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/falhas-equipamento")
public class FalhaEquipamentoController {
    private final FalhaEquipamentoService service;
    public FalhaEquipamentoController(FalhaEquipamentoService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<FalhaEquipamentoResponseDTO> criar(@Valid @RequestBody FalhaEquipamentoRequestDTO request) {
        var response = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
    @GetMapping
    public PageResponseDTO<FalhaEquipamentoResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) { return service.listar(params); }
    @GetMapping("/{id}")
    public FalhaEquipamentoResponseDTO obter(@PathVariable @Positive Long id) { return service.obterPorId(id); }
}
