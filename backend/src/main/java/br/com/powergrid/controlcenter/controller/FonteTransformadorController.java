package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.FonteMedicaoRequestDTO;
import br.com.powergrid.controlcenter.dto.FonteTransformadorResponseDTO;
import br.com.powergrid.controlcenter.service.FonteMedicaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transformadores/{id}/fontes")
public class FonteTransformadorController {
    private final FonteMedicaoService service;
    public FonteTransformadorController(FonteMedicaoService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FonteTransformadorResponseDTO criar(@PathVariable @Positive Long id,
            @Valid @RequestBody FonteMedicaoRequestDTO request) {
        return service.criarParaTransformador(id, request);
    }
}
