package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.FonteMedicaoRequestDTO;
import br.com.powergrid.controlcenter.dto.FonteMedicaoResponseDTO;
import br.com.powergrid.controlcenter.service.FonteMedicaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/linhas-transmissao/{linhaId}/fontes")
public class FonteMedicaoController {
    private final FonteMedicaoService service;
    public FonteMedicaoController(FonteMedicaoService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FonteMedicaoResponseDTO criar(@PathVariable @Positive Long linhaId,
            @Valid @RequestBody FonteMedicaoRequestDTO request) {
        return service.criar(linhaId, request);
    }
}
