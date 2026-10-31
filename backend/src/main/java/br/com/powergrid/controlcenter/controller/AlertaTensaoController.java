package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaTensaoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaTensaoService;
import br.com.powergrid.controlcenter.service.UsuarioAutenticadoService;
import org.springframework.security.core.Authentication;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas-tensao")
public class AlertaTensaoController {
    private final AlertaTensaoService service;
    private final UsuarioAutenticadoService usuarios;
    public AlertaTensaoController(AlertaTensaoService service, UsuarioAutenticadoService usuarios) {
        this.service = service; this.usuarios = usuarios;
    }

    @PatchMapping("/{id}/reconhecimento")
    public AlertaTensaoResponseDTO reconhecer(@PathVariable @Positive Long id, Authentication authentication) {
        return service.reconhecer(id, usuarios.consultar(authentication.getName()).id());
    }

    @GetMapping
    public PageResponseDTO<AlertaTensaoResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
    @GetMapping("/{id}")
    public AlertaTensaoResponseDTO buscarPorId(@PathVariable @Positive Long id) { return service.buscarPorId(id); }
}
