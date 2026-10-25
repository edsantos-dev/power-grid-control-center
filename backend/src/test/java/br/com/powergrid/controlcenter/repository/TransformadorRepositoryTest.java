package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.Transformador;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
class TransformadorRepositoryTest {

	@Autowired
	private TransformadorRepository transformadorRepository;

	@Autowired
	private AlimentadorRepository alimentadorRepository;

	@Autowired
	private SubestacaoRepository subestacaoRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	void devePersistirCadeiaERecuperarSubestacaoDerivada(EstadoOperacional estado) {
		Alimentador alimentador = salvarCadeia();
		Long subestacaoId = alimentador.getSubestacao().getId();
		Transformador transformador = novoTransformador(alimentador);
		transformador.setEstadoOperacional(estado);
		Transformador salvo = transformadorRepository.saveAndFlush(transformador);
		entityManager.clear();

		assertThat(salvo.getId()).isPositive().isNotEqualTo(alimentador.getId()).isNotEqualTo(subestacaoId);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM ativo_rede WHERE id = ?", Long.class, salvo.getId())).isEqualTo(salvo.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM transformador WHERE id = ?", Long.class, salvo.getId())).isEqualTo(salvo.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT alimentador_id FROM transformador WHERE id = ?", Long.class, salvo.getId()))
				.isEqualTo(alimentador.getId());

		Transformador recarregado = transformadorRepository.findById(salvo.getId()).orElseThrow();
		assertThat(recarregado).isNotSameAs(salvo);
		assertThat(recarregado.getNome()).isEqualTo(salvo.getNome());
		assertThat(recarregado.getLatitude()).isEqualByComparingTo(salvo.getLatitude());
		assertThat(recarregado.getLongitude()).isEqualByComparingTo(salvo.getLongitude());
		assertThat(recarregado.getCapacidadeMva()).isEqualByComparingTo(salvo.getCapacidadeMva());
		assertThat(recarregado.getEstadoOperacional()).isEqualTo(estado);
		assertThat(recarregado.getDataInstalacao()).isEqualTo(salvo.getDataInstalacao());
		assertThat(Hibernate.isInitialized(recarregado.getAlimentador())).isFalse();
		assertThat(recarregado.getAlimentador().getId()).isEqualTo(alimentador.getId());
		assertThat(recarregado.getAlimentador().getSubestacao().getId()).isEqualTo(subestacaoId);
		assertThat(recarregado.getAlimentador().getSubestacao().getNome()).isEqualTo("Subestação Central");
		entityManager.clear();
		assertThat(entityManager.find(AtivoRede.class, salvo.getId())).isInstanceOf(Transformador.class);
	}

	@Test
	void deveRejeitarAlimentadorNuloNoBanco() {
		assertViolacaoDeIntegridade(novoTransformador(null), "23502");
	}

	@Test
	void deveRejeitarAlimentadorInexistenteNoBanco() {
		Alimentador referencia = entityManager.getReference(Alimentador.class, Long.MAX_VALUE);
		assertViolacaoDeIntegridade(novoTransformador(referencia), "23506");
	}

	@Test
	void deveRejeitarCapacidadeNulaNoBanco() {
		Transformador transformador = novoTransformador(salvarCadeia());
		transformador.setCapacidadeMva(null);
		assertViolacaoDeIntegridade(transformador, "23502");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-0.000001"})
	void deveRejeitarCapacidadeNaoPositivaNoBanco(String capacidade) {
		Transformador transformador = novoTransformador(salvarCadeia());
		transformador.setCapacidadeMva(new BigDecimal(capacidade));
		assertViolacaoDeIntegridade(transformador, "23513");
	}

	private Alimentador salvarCadeia() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 15));
		subestacao.setTensaoNominalKv(new BigDecimal("138.000000"));
		subestacao.setCapacidadeMva(new BigDecimal("100.000000"));
		subestacaoRepository.saveAndFlush(subestacao);

		Alimentador alimentador = new Alimentador();
		alimentador.setNome("Alimentador Central");
		alimentador.setLatitude(new BigDecimal("-25.4284002"));
		alimentador.setLongitude(new BigDecimal("-49.2733002"));
		alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		alimentador.setDataInstalacao(LocalDate.of(2099, 2, 15));
		alimentador.setTensaoNominalKv(new BigDecimal("13.800000"));
		alimentador.setSubestacao(subestacao);
		return alimentadorRepository.saveAndFlush(alimentador);
	}

	private Transformador novoTransformador(Alimentador alimentador) {
		Transformador transformador = new Transformador();
		transformador.setNome("Transformador Central");
		transformador.setLatitude(new BigDecimal("-25.4284003"));
		transformador.setLongitude(new BigDecimal("-49.2733003"));
		transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		transformador.setDataInstalacao(LocalDate.of(2099, 3, 15));
		transformador.setCapacidadeMva(new BigDecimal("5.123456"));
		transformador.setAlimentador(alimentador);
		return transformador;
	}

	private void assertViolacaoDeIntegridade(Transformador transformador, String sqlState) {
		DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
				() -> transformadorRepository.saveAndFlush(transformador));
		assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
				cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
	}

}
