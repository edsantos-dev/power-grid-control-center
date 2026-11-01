package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.ManutencaoHistoricoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.ManutencaoService;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ativos-rede/{ativoId}/manutencoes")
public class ManutencaoAtivoController {
    private final ManutencaoService service;

    public ManutencaoAtivoController(ManutencaoService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponseDTO<ManutencaoHistoricoResponseDTO> listar(@PathVariable @Positive Long ativoId,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listar(ativoId, params);
    }
}
