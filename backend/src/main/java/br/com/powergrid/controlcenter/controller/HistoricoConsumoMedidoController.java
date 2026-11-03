package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.HistoricoConsumoMedidoResponseDTO;
import br.com.powergrid.controlcenter.service.HistoricoConsumoMedidoService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alimentadores/{id}/historico-consumo-medido")
public class HistoricoConsumoMedidoController {
    private final HistoricoConsumoMedidoService service;

    public HistoricoConsumoMedidoController(HistoricoConsumoMedidoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public HistoricoConsumoMedidoResponseDTO consultar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return service.consultar(id, params);
    }
}
