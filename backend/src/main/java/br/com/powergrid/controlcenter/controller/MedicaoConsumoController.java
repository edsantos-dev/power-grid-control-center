package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.MedicaoConsumoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/medidores/{id}")
public class MedicaoConsumoController {
    private final MedicaoConsumoService service;

    public MedicaoConsumoController(MedicaoConsumoService service) { this.service = service; }

    @PostMapping("/fontes")
    @ResponseStatus(HttpStatus.CREATED)
    public FonteMedidorResponseDTO criarFonte(@PathVariable @Positive Long id, @Valid @RequestBody FonteMedidorRequestDTO request) {
        return service.criarFonte(id, request);
    }

    @PostMapping("/medicoes-consumo")
    public ResponseEntity<MedicaoConsumoResponseDTO> ingerir(@PathVariable @Positive Long id, @Valid @RequestBody MedicaoConsumoRequestDTO request) {
        var resposta = service.ingerir(id, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{medicaoId}").buildAndExpand(resposta.id()).toUri();
        return ResponseEntity.created(location).body(resposta);
    }

    @GetMapping("/medicoes-consumo/{medicaoId}")
    public MedicaoConsumoResponseDTO obter(@PathVariable @Positive Long id, @PathVariable @Positive Long medicaoId) {
        return service.obter(id, medicaoId);
    }
}
