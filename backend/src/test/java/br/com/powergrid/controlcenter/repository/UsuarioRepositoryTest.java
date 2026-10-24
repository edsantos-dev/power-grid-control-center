package br.com.powergrid.controlcenter.repository;

import java.sql.SQLException;

import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class UsuarioRepositoryTest {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private EntityManager entityManager;

	@ParameterizedTest
	@EnumSource(PerfilAcesso.class)
	void devePersistirERecarregarUsuarioComCadaPerfil(PerfilAcesso perfil) {
		Usuario usuario = novoUsuario("MAT-001", "operador@example.com");
		usuario.setPerfil(perfil);
		usuario.setSenhaHash("valor-opaco-de-teste");

		Usuario salvo = usuarioRepository.saveAndFlush(usuario);
		entityManager.clear();

		Usuario recarregado = usuarioRepository.findById(salvo.getId()).orElseThrow();

		assertThat(recarregado.getId()).isPositive();
		assertThat(recarregado).isNotSameAs(salvo);
		assertThat(recarregado).usingRecursiveComparison().isEqualTo(salvo);
	}

	@Test
	void deveRejeitarEmailNulo() {
		Usuario usuario = novoUsuario("MAT-001", null);

		assertViolacaoDeIntegridade(usuario, "23502");
	}

	@Test
	void deveRejeitarMatriculaNula() {
		Usuario usuario = novoUsuario(null, "operador@example.com");

		assertViolacaoDeIntegridade(usuario, "23502");
	}

	@Test
	void deveRejeitarPerfilNulo() {
		Usuario usuario = novoUsuario("MAT-001", "operador@example.com");
		usuario.setPerfil(null);

		assertViolacaoDeIntegridade(usuario, "23502");
	}

	@Test
	void deveRejeitarEmailDuplicado() {
		usuarioRepository.saveAndFlush(novoUsuario("MAT-001", "operador@example.com"));
		Usuario duplicado = novoUsuario("MAT-002", "operador@example.com");

		assertViolacaoDeIntegridade(duplicado, "23505");
	}

	@Test
	void deveRejeitarMatriculaDuplicada() {
		usuarioRepository.saveAndFlush(novoUsuario("MAT-001", "operador@example.com"));
		Usuario duplicado = novoUsuario("MAT-001", "outro@example.com");

		assertViolacaoDeIntegridade(duplicado, "23505");
	}

	private Usuario novoUsuario(String matricula, String email) {
		Usuario usuario = new Usuario();
		usuario.setNome("Operador de teste");
		usuario.setMatricula(matricula);
		usuario.setEmail(email);
		usuario.setPerfil(PerfilAcesso.OPERADOR);
		usuario.setCargoProfissional("Engenheiro eletricista");
		return usuario;
	}

	private void assertViolacaoDeIntegridade(Usuario usuario, String sqlState) {
		DataIntegrityViolationException exception = assertThrows(
				DataIntegrityViolationException.class,
				() -> usuarioRepository.saveAndFlush(usuario));

		assertThat(exception.getMostSpecificCause())
				.isInstanceOfSatisfying(SQLException.class,
						cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
	}

}
