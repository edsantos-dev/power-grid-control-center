package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.MedicaoLinhaRequestDTO;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaResponseDTO;
import br.com.powergrid.controlcenter.service.MedicaoLinhaService;
import jakarta.validation.Valid;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/linhas-transmissao/{linhaId}/medicoes")
public class MedicaoLinhaController {
    private final MedicaoLinhaService service;

    public MedicaoLinhaController(MedicaoLinhaService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<MedicaoLinhaResponseDTO> listar(@PathVariable @Positive Long linhaId,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listar(linhaId, params);
    }

    @PostMapping
    public ResponseEntity<MedicaoLinhaResponseDTO> ingerir(@PathVariable @Positive Long linhaId,
            @Valid @RequestBody MedicaoLinhaRequestDTO request) {
        MedicaoLinhaResponseDTO response = service.ingerir(linhaId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri()).body(response);
    }
}
