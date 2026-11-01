package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.UsinaGeradoraService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usinas")
public class UsinaGeradoraController {
    private final UsinaGeradoraService service;

    public UsinaGeradoraController(UsinaGeradoraService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<UsinaResponseDTO> criar(@Valid @RequestBody UsinaRequestDTO request) {
        var resposta = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(resposta.id()).toUri();
        return ResponseEntity.created(location).body(resposta);
    }

    @PutMapping("/{id}")
    public UsinaResponseDTO atualizar(@PathVariable @Positive Long id, @Valid @RequestBody UsinaRequestDTO request) {
        return service.atualizar(id, request);
    }

    @GetMapping("/{id}")
    public UsinaResponseDTO obterPorId(@PathVariable @Positive Long id) { return service.obterPorId(id); }

    @GetMapping
    public PageResponseDTO<UsinaResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listarUsinas(params);
    }
}
