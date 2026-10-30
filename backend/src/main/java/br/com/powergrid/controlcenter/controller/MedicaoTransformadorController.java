package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.MedicaoTransformadorRequestDTO;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorResponseDTO;
import br.com.powergrid.controlcenter.service.MedicaoTransformadorService;
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
@RequestMapping("/api/transformadores/{id}/medicoes")
public class MedicaoTransformadorController {
    private final MedicaoTransformadorService service;

    public MedicaoTransformadorController(MedicaoTransformadorService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<MedicaoTransformadorResponseDTO> listar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listar(id, params);
    }

    @PostMapping
    public ResponseEntity<MedicaoTransformadorResponseDTO> ingerir(@PathVariable @Positive Long id,
            @Valid @RequestBody MedicaoTransformadorRequestDTO request) {
        MedicaoTransformadorResponseDTO response = service.ingerir(id, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri()).body(response);
    }
}
