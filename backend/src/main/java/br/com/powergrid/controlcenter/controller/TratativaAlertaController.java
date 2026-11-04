package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.AlertaService;
import br.com.powergrid.controlcenter.service.HistoricoAlertaService;
import br.com.powergrid.controlcenter.service.UsuarioAutenticadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas/{id}")
public class TratativaAlertaController {
    private final HistoricoAlertaService historicos;
    private final UsuarioAutenticadoService usuarios;
    private final AlertaService alertas;

    public TratativaAlertaController(HistoricoAlertaService historicos, UsuarioAutenticadoService usuarios, AlertaService alertas) {
        this.historicos = historicos;
        this.usuarios = usuarios;
        this.alertas = alertas;
    }

    @PatchMapping("/reconhecimento")
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public AlertaResponseDTO reconhecer(@PathVariable @Positive Long id, @Valid @RequestBody TratativaAlertaRequestDTO request,
            Authentication authentication) {
        return historicos.reconhecer(id, usuarios.consultar(authentication.getName()).id(), request);
    }

    @PatchMapping("/resolucao")
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public AlertaResponseDTO resolver(@PathVariable @Positive Long id, @Valid @RequestBody TratativaAlertaRequestDTO request,
            Authentication authentication) {
        return historicos.resolver(id, usuarios.consultar(authentication.getName()).id(), request);
    }

    @GetMapping("/historico")
    public PageResponseDTO<HistoricoAlertaResponseDTO> listar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return historicos.listar(id, params);
    }

    @GetMapping
    public AlertaResponseDTO obterPorId(@PathVariable @Positive Long id) {
        return alertas.obterPorId(id);
    }
}
