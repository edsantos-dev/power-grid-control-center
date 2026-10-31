package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AvaliacaoComunicacaoResultDTO;
import br.com.powergrid.controlcenter.service.AlertaComunicacaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class MonitoramentoComunicacaoController {
    private final AlertaComunicacaoService service;

    public MonitoramentoComunicacaoController(AlertaComunicacaoService service) {
        this.service = service;
    }

    @PostMapping("/avaliar-comunicacao")
    @PreAuthorize("hasAnyRole('OPERADOR', 'GERENTE_OPERACOES')")
    public AvaliacaoComunicacaoResultDTO avaliar() {
        return service.avaliar();
    }
}
