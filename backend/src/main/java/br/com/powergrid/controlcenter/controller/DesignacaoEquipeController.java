package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.service.HistoricoDesignacaoEquipeService;
import br.com.powergrid.controlcenter.service.UsuarioAutenticadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eventos-operacionais/{id}")
public class DesignacaoEquipeController {
    private final HistoricoDesignacaoEquipeService service;
    private final UsuarioAutenticadoService usuarios;

    public DesignacaoEquipeController(HistoricoDesignacaoEquipeService service, UsuarioAutenticadoService usuarios) {
        this.service = service;
        this.usuarios = usuarios;
    }

    @PutMapping("/equipe-designada")
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public EventoOperacionalResponseDTO designar(@PathVariable @Positive Long id,
            @Valid @RequestBody DesignacaoEquipeRequestDTO request, Authentication authentication) {
        Long usuarioId = usuarios.consultar(authentication.getName()).id();
        return service.designar(id, request, usuarioId);
    }

    @GetMapping("/designacoes")
    public PageResponseDTO<HistoricoDesignacaoResponseDTO> listar(@PathVariable @Positive Long id,
            @RequestParam MultiValueMap<String, String> params) {
        return service.listar(id, params);
    }
}
