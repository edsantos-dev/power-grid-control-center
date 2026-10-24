package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class SubestacaoRepositoryTest {

	@Autowired
	private SubestacaoRepository subestacaoRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	void devePersistirNasDuasTabelasERecarregarTodosOsCampos(EstadoOperacional estado) {
		Subestacao subestacao = novaSubestacao();
		subestacao.setEstadoOperacional(estado);
		Subestacao salva = subestacaoRepository.saveAndFlush(subestacao);
		entityManager.clear();

		assertThat(salva.getId()).isPositive();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM ativo_rede WHERE id = ?", Long.class, salva.getId())).isEqualTo(salva.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM subestacao WHERE id = ?", Long.class, salva.getId())).isEqualTo(salva.getId());

		Subestacao recarregada = subestacaoRepository.findById(salva.getId()).orElseThrow();
		assertThat(recarregada).isNotSameAs(salva);
		assertThat(recarregada).usingRecursiveComparison().isEqualTo(salva);
		entityManager.clear();
		AtivoRede ativo = entityManager.find(AtivoRede.class, salva.getId());
		assertThat(ativo).isInstanceOf(Subestacao.class);
		assertThat(ativo.getId()).isEqualTo(salva.getId());
	}

	@Test
	void deveAtualizarCamposDaBaseEDoSubtipo() {
		Subestacao salva = subestacaoRepository.saveAndFlush(novaSubestacao());
		salva.setNome("Subestação atualizada");
		salva.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		salva.setCapacidadeMva(new BigDecimal("150.123456"));
		subestacaoRepository.saveAndFlush(salva);
		entityManager.clear();

		Subestacao atualizada = subestacaoRepository.findById(salva.getId()).orElseThrow();
		assertThat(atualizada.getNome()).isEqualTo("Subestação atualizada");
		assertThat(atualizada.getEstadoOperacional()).isEqualTo(EstadoOperacional.MANUTENCAO);
		assertThat(atualizada.getCapacidadeMva()).isEqualByComparingTo("150.123456");
	}

	@Test
	void deveRejeitarEstadoOperacionalNuloNoBanco() {
		Subestacao subestacao = novaSubestacao();
		subestacao.setEstadoOperacional(null);

		assertViolacaoDeIntegridade(subestacao, "23502");
	}

	@ParameterizedTest
	@CsvSource({"-90.0000000, -180.0000000", "90.0000000, 180.0000000"})
	void deveAceitarLimitesDasCoordenadas(String latitude, String longitude) {
		Subestacao subestacao = novaSubestacao();
		subestacao.setLatitude(new BigDecimal(latitude));
		subestacao.setLongitude(new BigDecimal(longitude));
		Subestacao salva = subestacaoRepository.saveAndFlush(subestacao);
		entityManager.clear();

		Subestacao recarregada = subestacaoRepository.findById(salva.getId()).orElseThrow();
		assertThat(recarregada.getLatitude()).isEqualByComparingTo(latitude);
		assertThat(recarregada.getLongitude()).isEqualByComparingTo(longitude);
	}

	@ParameterizedTest
	@CsvSource({
			"-90.0000001, 0.0000000",
			"90.0000001, 0.0000000",
			"0.0000000, -180.0000001",
			"0.0000000, 180.0000001"
	})
	void deveRejeitarCoordenadasForaDosLimitesNoBanco(String latitude, String longitude) {
		Subestacao subestacao = novaSubestacao();
		subestacao.setLatitude(new BigDecimal(latitude));
		subestacao.setLongitude(new BigDecimal(longitude));

		assertViolacaoDeIntegridade(subestacao, "23513");
	}

	@Test
	void deveRejeitarEstadoForaDoCatalogoNoBanco() {
		Subestacao salva = subestacaoRepository.saveAndFlush(novaSubestacao());
		DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("UPDATE ativo_rede SET estado_operacional = ? WHERE id = ?",
						"INVALIDO", salva.getId()));

		assertSqlState(exception, "23513");
	}

	private Subestacao novaSubestacao() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 15));
		subestacao.setTensaoNominalKv(new BigDecimal("138.123456"));
		subestacao.setCapacidadeMva(new BigDecimal("100.654321"));
		return subestacao;
	}

	private void assertViolacaoDeIntegridade(Subestacao subestacao, String sqlState) {
		DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
				() -> subestacaoRepository.saveAndFlush(subestacao));
		assertSqlState(exception, sqlState);
	}

	private void assertSqlState(DataIntegrityViolationException exception, String sqlState) {
		assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
				cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
	}

}
