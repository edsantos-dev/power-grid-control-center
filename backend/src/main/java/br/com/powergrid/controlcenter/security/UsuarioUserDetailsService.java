package br.com.powergrid.controlcenter.security;

import java.util.List;

import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioUserDetailsService implements UserDetailsService {

	private final UsuarioRepository usuarioRepository;

	public UsuarioUserDetailsService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		String emailNormalizado = NormalizadorEmail.normalizar(email);
		if (emailNormalizado == null || emailNormalizado.isEmpty()) {
			throw new UsernameNotFoundException("Usuário não encontrado.");
		}

		Usuario usuario = usuarioRepository.findByEmail(emailNormalizado)
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));

		if (usuario.getSenhaHash() == null || usuario.getSenhaHash().isBlank()) {
			throw new UsernameNotFoundException("Usuário não encontrado.");
		}

		return new User(usuario.getEmail(), usuario.getSenhaHash(),
				List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
	}

}
