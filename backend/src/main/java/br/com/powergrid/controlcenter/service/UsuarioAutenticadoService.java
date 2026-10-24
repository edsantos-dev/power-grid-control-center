package br.com.powergrid.controlcenter.service;

import br.com.powergrid.controlcenter.dto.UsuarioAutenticadoResponse;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioAutenticadoService {

	private final UsuarioRepository usuarioRepository;

	public UsuarioAutenticadoService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Transactional(readOnly = true)
	public UsuarioAutenticadoResponse consultar(String email) {
		Usuario usuario = usuarioRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
		return new UsuarioAutenticadoResponse(
				usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
	}

}
