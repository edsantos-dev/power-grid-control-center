package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
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
class MedicaoTransformadorRepositoryTest {
    @Autowired private MedicaoTransformadorRepository repository;
    @Autowired private FonteMedicaoRepository fontes;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(strings = {"0", "8.5", "10", "10.8"})
    void devePersistirInclusiveSobrecarga(String potencia) {
        MedicaoTransformador m = leitura(); m.setPotenciaAparenteMva(new BigDecimal(potencia));
        repository.saveAndFlush(m); Long ativoId = m.getFonte().getAtivo().getId(); entityManager.clear();
        MedicaoTransformador salvo = repository.findById(m.getId()).orElseThrow();
        assertThat(salvo.getPotenciaAparenteMva()).isEqualByComparingTo(potencia);
        assertThat(salvo.getCapacidadeReferenciaMva()).isEqualByComparingTo("10");
        assertThat(salvo.getMedidoEm().toInstant()).isEqualTo(m.getMedidoEm().toInstant());
        assertThat(salvo.getRecebidoEm().toInstant()).isEqualTo(m.getRecebidoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getFonte())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getFonte().getAtivo())).isFalse();
        assertThat(Hibernate.getClass(salvo.getFonte().getAtivo())).isEqualTo(Transformador.class);
        assertThat(salvo.getFonte().getAtivo().getId()).isEqualTo(ativoId);
        assertThat(fontes.findByAtivoIdOrderByIdAsc(ativoId)).extracting(FonteMedicao::getId).containsExactly(m.getFonte().getId());
        if (potencia.equals("10.8")) assertThat(salvo.getPotenciaAparenteMva()).isGreaterThan(salvo.getCapacidadeReferenciaMva());
    }

    @Test
    void deveManterSnapshotAposAlterarCapacidadeCadastral() {
        MedicaoTransformador m = repository.saveAndFlush(leitura());
        Transformador t = entityManager.find(Transformador.class, m.getFonte().getAtivo().getId());
        t.setCapacidadeMva(new BigDecimal("20")); entityManager.flush(); entityManager.clear();
        assertThat(repository.findById(m.getId()).orElseThrow().getCapacidadeReferenciaMva()).isEqualByComparingTo("10");
        assertThat(entityManager.find(Transformador.class, t.getId()).getCapacidadeMva()).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte_id", "medido_em", "recebido_em", "potencia_aparente_mva", "capacidade_referencia_mva"})
    void deveRejeitarNulos(String campo) {
        MedicaoTransformador m = leitura();
        switch (campo) {
            case "fonte_id" -> m.setFonte(null);
            case "medido_em" -> m.setMedidoEm(null);
            case "recebido_em" -> m.setRecebidoEm(null);
            case "potencia_aparente_mva" -> m.setPotenciaAparenteMva(null);
            default -> m.setCapacidadeReferenciaMva(null);
        }
        violacao(m, "23502", campo);
    }

    @Test
    void deveRejeitarPotenciaNegativa() {
        MedicaoTransformador m = leitura(); m.setPotenciaAparenteMva(new BigDecimal("-0.000001"));
        violacao(m, "23513", "ck_medicao_transformador_potencia");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void deveRejeitarCapacidadeNaoPositiva(String valor) {
        MedicaoTransformador m = leitura(); m.setCapacidadeReferenciaMva(new BigDecimal(valor));
        violacao(m, "23513", "ck_medicao_transformador_capacidade");
    }

    @Test
    void deveRejeitarFonteInexistente() {
        MedicaoTransformador m = leitura(); m.setFonte(entityManager.getReference(FonteMedicao.class, Long.MAX_VALUE));
        violacao(m, "23506", "fk_medicao_transformador_fonte");
    }

    private FonteMedicao fonte() {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação"); subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(subestacao); entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador"); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(transformador);
        FonteMedicao fonte = new FonteMedicao(); fonte.setIdentificador("SIMULADOR-TRAFO");
        fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setAtivo(transformador);
        entityManager.persist(fonte); entityManager.flush(); return fonte;
    }

    private MedicaoTransformador leitura() {
        MedicaoTransformador m = new MedicaoTransformador(); m.setFonte(fonte());
        m.setMedidoEm(OffsetDateTime.parse("2026-10-30T09:00:00-03:00"));
        m.setRecebidoEm(OffsetDateTime.parse("2026-10-30T12:00:05Z"));
        m.setPotenciaAparenteMva(new BigDecimal("8.5"));
        m.setCapacidadeReferenciaMva(new BigDecimal("10")); return m;
    }

    private void violacao(MedicaoTransformador m, String state, String constraint) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(m));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(state);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
}
