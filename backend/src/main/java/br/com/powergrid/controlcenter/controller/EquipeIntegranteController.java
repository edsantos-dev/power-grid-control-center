package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.EquipeManutencaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipes/{id}")
public class EquipeIntegranteController {
    private final EquipeManutencaoService service;

    public EquipeIntegranteController(EquipeManutencaoService service) {
        this.service = service;
    }

    @GetMapping("/integrantes")
    public PageResponseDTO<IntegranteResponseDTO> listar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listarIntegrantes(id, params);
    }

    @PutMapping("/integrantes/{usuarioId}")
    public ResponseEntity<IntegranteResponseDTO> adicionar(@PathVariable @Positive Long id,
            @PathVariable @Positive Long usuarioId) {
        var resultado = service.adicionarIntegrante(id, usuarioId);
        return ResponseEntity.status(resultado.criado() ? 201 : 200).body(resultado.integrante());
    }

    @DeleteMapping("/integrantes/{usuarioId}")
    public ResponseEntity<Void> remover(@PathVariable @Positive Long id, @PathVariable @Positive Long usuarioId) {
        service.removerIntegrante(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/situacao")
    public EquipeResponseDTO situacao(@PathVariable @Positive Long id, @Valid @RequestBody SituacaoEquipeRequestDTO request) {
        return service.atualizarSituacao(id, request.situacao());
    }
}
