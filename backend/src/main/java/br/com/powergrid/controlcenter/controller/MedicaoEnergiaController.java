package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.MedicaoEnergiaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alimentadores/{id}")
public class MedicaoEnergiaController {
    private final MedicaoEnergiaService service;

    public MedicaoEnergiaController(MedicaoEnergiaService service) { this.service = service; }

    @PatchMapping("/parametros-perdas")
    public AlimentadorParametroResponseDTO configurar(@PathVariable @Positive Long id,
            @Valid @RequestBody AlimentadorParametroRequestDTO request) {
        return service.configurar(id, request);
    }

    @PostMapping("/fontes")
    @ResponseStatus(HttpStatus.CREATED)
    public FonteAlimentadorResponseDTO criarFonte(@PathVariable @Positive Long id, @Valid @RequestBody FonteMedicaoRequestDTO request) {
        return service.criarFonte(id, request);
    }

    @PostMapping("/fontes/{fonteId}/medicoes-energia")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicaoEnergiaResponseDTO ingerir(@PathVariable @Positive Long id, @PathVariable @Positive Long fonteId,
            @Valid @RequestBody MedicaoEnergiaRequestDTO request) {
        return service.ingerir(id, fonteId, request);
    }
}
