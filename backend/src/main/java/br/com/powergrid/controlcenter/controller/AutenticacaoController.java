package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.UsuarioAutenticadoResponse;
import br.com.powergrid.controlcenter.service.UsuarioAutenticadoService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

	private final UsuarioAutenticadoService usuarioAutenticadoService;

	public AutenticacaoController(UsuarioAutenticadoService usuarioAutenticadoService) {
		this.usuarioAutenticadoService = usuarioAutenticadoService;
	}

	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getToken(), token.getHeaderName());
	}

	@GetMapping("/me")
	public UsuarioAutenticadoResponse usuarioAutenticado(Authentication authentication) {
		return usuarioAutenticadoService.consultar(authentication.getName());
	}

}
