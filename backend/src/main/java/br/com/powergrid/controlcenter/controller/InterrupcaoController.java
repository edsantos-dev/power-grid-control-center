package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.dto.InterrupcaoRequestDTO;
import br.com.powergrid.controlcenter.service.EventoOperacionalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import org.springframework.util.MultiValueMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/interrupcoes")
public class InterrupcaoController {
    private final EventoOperacionalService service;

    public InterrupcaoController(EventoOperacionalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventoOperacionalResponseDTO> criar(@Valid @RequestBody InterrupcaoRequestDTO request) {
        var resposta = service.criarInterrupcao(request);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/eventos-operacionais/{id}").buildAndExpand(resposta.id()).toUri();
        return ResponseEntity.created(location).body(resposta);
    }

    @GetMapping("/{id}")
    public EventoOperacionalResponseDTO obterPorId(@PathVariable @Positive Long id) {
        return service.obterInterrupcao(id);
    }

    @GetMapping
    public PageResponseDTO<EventoOperacionalResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listarInterrupcoes(params);
    }
}
