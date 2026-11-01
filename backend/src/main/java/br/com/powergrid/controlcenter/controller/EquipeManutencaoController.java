package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.EquipeRequestDTO;
import br.com.powergrid.controlcenter.dto.EquipeResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.EquipeManutencaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/equipes")
public class EquipeManutencaoController {
    private final EquipeManutencaoService service;

    public EquipeManutencaoController(EquipeManutencaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EquipeResponseDTO> criar(@Valid @RequestBody EquipeRequestDTO request) {
        var resposta = service.criarEquipe(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(resposta.id()).toUri();
        return ResponseEntity.created(location).body(resposta);
    }

    @PutMapping("/{id}")
    public EquipeResponseDTO atualizar(@PathVariable @Positive Long id, @Valid @RequestBody EquipeRequestDTO request) {
        return service.atualizarEquipe(id, request);
    }

    @GetMapping("/{id}")
    public EquipeResponseDTO obterPorId(@PathVariable @Positive Long id) {
        return service.obterPorId(id);
    }

    @GetMapping
    public PageResponseDTO<EquipeResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listarEquipes(params);
    }
}
