package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.HistoricoEventoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.dto.TransacaoEventoRequestDTO;
import br.com.powergrid.controlcenter.service.HistoricoEventoOperacionalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/eventos-operacionais/{id}/transicoes")
public class TransacaoEventoController {
    private final HistoricoEventoOperacionalService service;

    public TransacaoEventoController(HistoricoEventoOperacionalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<HistoricoEventoResponseDTO> transicionar(@PathVariable @Positive Long id,
            @Valid @RequestBody TransacaoEventoRequestDTO request) {
        var response = service.transicionar(id, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{transicaoId}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponseDTO<HistoricoEventoResponseDTO> listar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listar(id, params);
    }

    @GetMapping("/{transicaoId}")
    public HistoricoEventoResponseDTO buscarPorId(@PathVariable @Positive Long id,
            @PathVariable @Positive Long transicaoId) {
        return service.buscarPorId(id, transicaoId);
    }
}
