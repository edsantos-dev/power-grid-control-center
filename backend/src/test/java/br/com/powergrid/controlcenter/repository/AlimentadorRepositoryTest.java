package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class AlimentadorRepositoryTest {

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
	void devePersistirNasDuasTabelasERecarregarRelacionamentoLazy(EstadoOperacional estado) {
		Subestacao subestacao = salvarSubestacao();
		Alimentador alimentador = novoAlimentador(subestacao);
		alimentador.setEstadoOperacional(estado);
		Alimentador salvo = alimentadorRepository.saveAndFlush(alimentador);
		entityManager.clear();

		assertThat(salvo.getId()).isPositive().isNotEqualTo(subestacao.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM ativo_rede WHERE id = ?", Long.class, salvo.getId())).isEqualTo(salvo.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM alimentador WHERE id = ?", Long.class, salvo.getId())).isEqualTo(salvo.getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT subestacao_id FROM alimentador WHERE id = ?", Long.class, salvo.getId()))
				.isEqualTo(subestacao.getId());

		Alimentador recarregado = alimentadorRepository.findById(salvo.getId()).orElseThrow();
		assertThat(recarregado).isNotSameAs(salvo);
		assertThat(recarregado.getNome()).isEqualTo(salvo.getNome());
		assertThat(recarregado.getLatitude()).isEqualByComparingTo(salvo.getLatitude());
		assertThat(recarregado.getLongitude()).isEqualByComparingTo(salvo.getLongitude());
		assertThat(recarregado.getTensaoNominalKv()).isEqualByComparingTo(salvo.getTensaoNominalKv());
		assertThat(recarregado.getEstadoOperacional()).isEqualTo(estado);
		assertThat(recarregado.getDataInstalacao()).isEqualTo(salvo.getDataInstalacao());
		assertThat(Hibernate.isInitialized(recarregado.getSubestacao())).isFalse();
		assertThat(recarregado.getSubestacao().getId()).isEqualTo(subestacao.getId());
		assertThat(recarregado.getSubestacao().getNome()).isEqualTo(subestacao.getNome());
		assertThat(Hibernate.isInitialized(recarregado.getSubestacao())).isTrue();
		entityManager.clear();
		assertThat(entityManager.find(AtivoRede.class, salvo.getId())).isInstanceOf(Alimentador.class);
	}

	@Test
	void devePermitirVariosAlimentadoresNaMesmaSubestacao() {
		Subestacao subestacao = salvarSubestacao();
		Alimentador primeiro = alimentadorRepository.saveAndFlush(novoAlimentador(subestacao));
		Alimentador segundo = alimentadorRepository.saveAndFlush(novoAlimentador(subestacao));
		entityManager.clear();

		assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
		assertThat(alimentadorRepository.findById(primeiro.getId()).orElseThrow().getSubestacao().getId())
				.isEqualTo(subestacao.getId());
		assertThat(alimentadorRepository.findById(segundo.getId()).orElseThrow().getSubestacao().getId())
				.isEqualTo(subestacao.getId());
		assertThat(subestacaoRepository.count()).isEqualTo(1);
	}

	@Test
	void deveRejeitarSubestacaoNulaNoBanco() {
		assertViolacaoDeIntegridade(novoAlimentador(null), "23502");
	}

	@Test
	void deveRejeitarReferenciaASubestacaoInexistenteNoBanco() {
		Subestacao referencia = entityManager.getReference(Subestacao.class, Long.MAX_VALUE);
		assertViolacaoDeIntegridade(novoAlimentador(referencia), "23506");
	}

	private Subestacao salvarSubestacao() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 15));
		subestacao.setTensaoNominalKv(new BigDecimal("138.000000"));
		subestacao.setCapacidadeMva(new BigDecimal("100.000000"));
		return subestacaoRepository.saveAndFlush(subestacao);
	}

	private Alimentador novoAlimentador(Subestacao subestacao) {
		Alimentador alimentador = new Alimentador();
		alimentador.setNome("Alimentador Central");
		alimentador.setLatitude(new BigDecimal("-25.4284002"));
		alimentador.setLongitude(new BigDecimal("-49.2733002"));
		alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
		alimentador.setDataInstalacao(LocalDate.of(2099, 2, 15));
		alimentador.setTensaoNominalKv(new BigDecimal("13.812345"));
		alimentador.setSubestacao(subestacao);
		return alimentador;
	}

	private void assertViolacaoDeIntegridade(Alimentador alimentador, String sqlState) {
		DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
				() -> alimentadorRepository.saveAndFlush(alimentador));
		assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
				cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
	}

}
