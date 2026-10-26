package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;
import br.com.powergrid.controlcenter.dto.MedidorRequestDTO;
import br.com.powergrid.controlcenter.dto.MedidorInstalacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import br.com.powergrid.controlcenter.service.MedidorInteligenteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/medidores")
public class MedidorInteligenteController {
    private final MedidorInteligenteService service;

    public MedidorInteligenteController(MedidorInteligenteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MedidorResponseDTO> criar(@Valid @RequestBody MedidorRequestDTO request) {
        MedidorResponseDTO response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<MedidorResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public MedidorResponseDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PatchMapping("/{id}/instalacao")
    public MedidorResponseDTO instalar(@PathVariable Long id,
            @Valid @RequestBody MedidorInstalacaoRequestDTO request) {
        return service.instalar(id, request);
    }
}
