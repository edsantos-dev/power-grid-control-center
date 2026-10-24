package br.com.powergrid.controlcenter.security;

import java.util.Optional;

import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioUserDetailsServiceTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@InjectMocks
	private UsuarioUserDetailsService userDetailsService;

	@ParameterizedTest
	@CsvSource({
			"OPERADOR, ROLE_OPERADOR",
			"GERENTE_OPERACOES, ROLE_GERENTE_OPERACOES",
			"TECNICO_MANUTENCAO, ROLE_TECNICO_MANUTENCAO"
	})
	void deveBuscarPorEmailNormalizadoEConverterPerfil(PerfilAcesso perfil, String autoridade) {
		Usuario usuario = novoUsuario();
		usuario.setPerfil(perfil);
		when(usuarioRepository.findByEmail("operador@example.com")).thenReturn(Optional.of(usuario));

		UserDetails resultado = userDetailsService.loadUserByUsername("  OPERADOR@Example.COM  ");

		assertThat(resultado.getUsername()).isEqualTo("operador@example.com");
		assertThat(resultado.getPassword()).isEqualTo(usuario.getSenhaHash());
		assertThat(resultado.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly(autoridade);
		verify(usuarioRepository).findByEmail("operador@example.com");
	}

	@Test
	void deveRejeitarUsuarioInexistente() {
		when(usuarioRepository.findByEmail("inexistente@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("inexistente@example.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void deveRejeitarEmailAusenteSemConsultarRepositorio(String email) {
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
				.isInstanceOf(UsernameNotFoundException.class);

		verifyNoInteractions(usuarioRepository);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void deveRejeitarUsuarioSemCredencial(String senhaHash) {
		Usuario usuario = novoUsuario();
		usuario.setSenhaHash(senhaHash);
		when(usuarioRepository.findByEmail("operador@example.com")).thenReturn(Optional.of(usuario));

		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("operador@example.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	private Usuario novoUsuario() {
		Usuario usuario = new Usuario();
		usuario.setEmail("operador@example.com");
		usuario.setSenhaHash("hash-armazenado-de-teste");
		usuario.setPerfil(PerfilAcesso.OPERADOR);
		usuario.setCargoProfissional("Gerente");
		return usuario;
	}

}
