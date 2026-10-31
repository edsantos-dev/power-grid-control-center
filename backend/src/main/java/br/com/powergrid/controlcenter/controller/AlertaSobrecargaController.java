package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.AlertaSobrecargaResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.service.AlertaSobrecargaService;
import br.com.powergrid.controlcenter.service.UsuarioAutenticadoService;
import org.springframework.security.core.Authentication;
import jakarta.validation.constraints.Positive;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas-sobrecarga")
public class AlertaSobrecargaController {
    private final AlertaSobrecargaService service;
    private final UsuarioAutenticadoService usuarios;
    public AlertaSobrecargaController(AlertaSobrecargaService service, UsuarioAutenticadoService usuarios) {
        this.service = service; this.usuarios = usuarios;
    }

    @PatchMapping("/{id}/reconhecimento")
    public AlertaSobrecargaResponseDTO reconhecer(@PathVariable @Positive Long id, Authentication authentication) {
        Long usuarioId = usuarios.consultar(authentication.getName()).id();
        return service.reconhecer(id, usuarioId);
    }

    @GetMapping
    public PageResponseDTO<AlertaSobrecargaResponseDTO> listar(@RequestParam MultiValueMap<String, String> params) {
        return service.listar(params);
    }
    @GetMapping("/{id}")
    public AlertaSobrecargaResponseDTO buscarPorId(@PathVariable @Positive Long id) {
        return service.buscarPorId(id);
    }
}
