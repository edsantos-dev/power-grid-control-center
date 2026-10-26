package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Consumidor;
import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;
import br.com.powergrid.controlcenter.model.MedidorInteligente;
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
class MedidorInteligenteRepositoryTest {
    @Autowired private MedidorInteligenteRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void devePersistirEstoqueComHerancaJoined(EstadoOperacional estado) {
        MedidorInteligente medidor = novo("0001-AbC");
        medidor.setEstadoOperacional(estado);
        assertThat(repository.existsByNumeroSerie(medidor.getNumeroSerie())).isFalse();
        repository.saveAndFlush(medidor);
        entityManager.clear();
        MedidorInteligente salvo = repository.findById(medidor.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getNome()).isEqualTo("Medidor de teste");
        assertThat(salvo.getNumeroSerie()).isEqualTo("0001-AbC");
        assertThat(salvo.getEstadoOperacional()).isEqualTo(estado);
        assertThat(salvo.getConsumidor()).isNull();
        assertThat(salvo.getTransformador()).isNull();
        assertThat(salvo.getLatitude()).isNull();
        assertThat(salvo.getLongitude()).isNull();
        assertThat(salvo.getDataInstalacao()).isNull();
        assertThat(salvo.getEnderecoFornecimento()).isNull();
        assertThat(repository.existsByNumeroSerie("0001-AbC")).isTrue();
        assertThat(repository.existsByNumeroSerie("0001-abc")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM ativo_rede WHERE id = ?", Long.class, salvo.getId()))
                .isEqualTo(salvo.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM medidor_inteligente WHERE id = ?", Long.class, salvo.getId()))
                .isEqualTo(salvo.getId());
        entityManager.clear();
        assertThat(entityManager.find(AtivoRede.class, salvo.getId())).isInstanceOf(MedidorInteligente.class);
    }

    @Test
    void deveAtualizarEstoqueParaInstaladoPreservandoIdentidade() {
        MedidorInteligente medidor = repository.saveAndFlush(novo("0001"));
        Long id = medidor.getId();
        Consumidor consumidor = salvarConsumidor();
        Transformador transformador = salvarTransformador();
        Long alimentadorId = transformador.getAlimentador().getId();
        Long subestacaoId = transformador.getAlimentador().getSubestacao().getId();
        entityManager.clear();
        MedidorInteligente instalando = repository.findById(id).orElseThrow();
        instalando.setConsumidor(entityManager.getReference(Consumidor.class, consumidor.getId()));
        instalando.setTransformador(entityManager.getReference(Transformador.class, transformador.getId()));
        instalando.setLatitude(new BigDecimal("-25.4284001"));
        instalando.setLongitude(new BigDecimal("-49.2733001"));
        instalando.setDataInstalacao(LocalDate.of(2026, 1, 15));
        instalando.setEnderecoFornecimento("Rua das Flores, 100");
        repository.saveAndFlush(instalando);
        entityManager.clear();
        MedidorInteligente salvo = repository.findById(id).orElseThrow();
        assertThat(salvo.getId()).isEqualTo(id);
        assertThat(salvo.getNumeroSerie()).isEqualTo("0001");
        assertThat(salvo.getEstadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(salvo.getLatitude()).isEqualByComparingTo("-25.4284001");
        assertThat(salvo.getLongitude()).isEqualByComparingTo("-49.2733001");
        assertThat(salvo.getDataInstalacao()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(salvo.getEnderecoFornecimento()).isEqualTo("Rua das Flores, 100");
        assertThat(Hibernate.isInitialized(salvo.getConsumidor())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getTransformador())).isFalse();
        assertThat(salvo.getConsumidor().getId()).isEqualTo(consumidor.getId());
        assertThat(salvo.getConsumidor().getNomeRazaoSocial()).isEqualTo("Titular de teste");
        assertThat(salvo.getTransformador().getId()).isEqualTo(transformador.getId());
        assertThat(salvo.getTransformador().getAlimentador().getId()).isEqualTo(alimentadorId);
        assertThat(salvo.getTransformador().getAlimentador().getSubestacao().getId()).isEqualTo(subestacaoId);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deveRejeitarNumeroSerieDuplicado() {
        repository.saveAndFlush(novo("0001"));
        assertViolacao(novo("0001"), "23505", "uq_medidor_numero_serie");
    }

    @Test
    void deveDistinguirMaiusculasEMinusculasNaUnicidade() {
        repository.saveAndFlush(novo("ABC-001"));
        repository.saveAndFlush(novo("abc-001"));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void deveRejeitarNumeroSerieNulo() {
        assertViolacao(novo(null), "23502", "numero_serie");
    }

    @ParameterizedTest
    @ValueSource(strings = {"consumidor", "transformador"})
    void deveRejeitarReferenciaInexistente(String vinculo) {
        MedidorInteligente medidor = novo("0001");
        if (vinculo.equals("consumidor")) {
            medidor.setConsumidor(entityManager.getReference(Consumidor.class, Long.MAX_VALUE));
        } else {
            medidor.setTransformador(entityManager.getReference(Transformador.class, Long.MAX_VALUE));
        }
        assertViolacao(medidor, "23506", "fk_medidor_" + vinculo);
    }

    private MedidorInteligente novo(String numeroSerie) {
        MedidorInteligente medidor = new MedidorInteligente();
        medidor.setNome("Medidor de teste");
        medidor.setNumeroSerie(numeroSerie);
        medidor.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        return medidor;
    }

    private Consumidor salvarConsumidor() {
        Consumidor consumidor = new Consumidor();
        consumidor.setNomeRazaoSocial("Titular de teste");
        consumidor.setTipoPessoa(TipoPessoa.PF);
        consumidor.setDocumento("52998224725");
        entityManager.persist(consumidor);
        entityManager.flush();
        return consumidor;
    }

    private Transformador salvarTransformador() {
        Subestacao subestacao = new Subestacao();
        preencher(subestacao);
        subestacao.setCapacidadeMva(new BigDecimal("100"));
        subestacao.setTensaoNominalKv(new BigDecimal("138"));
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        preencher(alimentador);
        alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
        alimentador.setSubestacao(subestacao);
        entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        preencher(transformador);
        transformador.setCapacidadeMva(new BigDecimal("5"));
        transformador.setAlimentador(alimentador);
        entityManager.persist(transformador);
        entityManager.flush();
        return transformador;
    }

    private void preencher(AtivoRede ativo) {
        ativo.setNome("Ativo de teste");
        ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        ativo.setLatitude(new BigDecimal("-25.4284"));
        ativo.setLongitude(new BigDecimal("-49.2733"));
        ativo.setDataInstalacao(LocalDate.of(2026, 1, 1));
    }

    private void assertViolacao(MedidorInteligente medidor, String sqlState, String constraint) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(medidor));
        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
}
