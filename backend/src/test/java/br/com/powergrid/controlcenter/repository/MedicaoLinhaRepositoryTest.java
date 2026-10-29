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
class MedicaoLinhaRepositoryTest {
    @Autowired private MedicaoLinhaRepository repository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(strings = {"tensao", "corrente", "ambas", "zero"})
    void devePersistirLeiturasParciaisECompletas(String tipo) {
        MedicaoLinha leitura = leitura();
        if (tipo.equals("tensao")) leitura.setCorrenteA(null);
        if (tipo.equals("corrente")) leitura.setTensaoKv(null);
        if (tipo.equals("zero")) {
            leitura.setTensaoKv(BigDecimal.ZERO);
            leitura.setCorrenteA(BigDecimal.ZERO);
        }
        repository.saveAndFlush(leitura);
        Long fonteId = leitura.getFonte().getId();
        entityManager.clear();
        MedicaoLinha salvo = repository.findById(leitura.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(Hibernate.isInitialized(salvo.getFonte())).isFalse();
        assertThat(salvo.getFonte().getId()).isEqualTo(fonteId);
        assertThat(salvo.getMedidoEm().toInstant()).isEqualTo(leitura.getMedidoEm().toInstant());
        assertThat(salvo.getRecebidoEm().toInstant()).isEqualTo(leitura.getRecebidoEm().toInstant());
        if (leitura.getTensaoKv() == null) assertThat(salvo.getTensaoKv()).isNull();
        else assertThat(salvo.getTensaoKv()).isEqualByComparingTo(leitura.getTensaoKv());
        if (leitura.getCorrenteA() == null) assertThat(salvo.getCorrenteA()).isNull();
        else assertThat(salvo.getCorrenteA()).isEqualByComparingTo(leitura.getCorrenteA());
    }

    @Test
    void deveRejeitarAmbasGrandezasNulas() {
        MedicaoLinha leitura = leitura();
        leitura.setTensaoKv(null);
        leitura.setCorrenteA(null);
        violacao(leitura, "23513", "ck_medicao_grandeza");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tensao", "corrente"})
    void deveRejeitarValoresNegativos(String campo) {
        MedicaoLinha leitura = leitura();
        if (campo.equals("tensao")) leitura.setTensaoKv(new BigDecimal("-0.000001"));
        else leitura.setCorrenteA(new BigDecimal("-0.000001"));
        violacao(leitura, "23513", "ck_medicao_" + campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte_id", "medido_em", "recebido_em"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        MedicaoLinha leitura = leitura();
        switch (campo) {
            case "fonte_id" -> leitura.setFonte(null);
            case "medido_em" -> leitura.setMedidoEm(null);
            default -> leitura.setRecebidoEm(null);
        }
        violacao(leitura, "23502", campo);
    }

    @Test
    void deveRejeitarFonteInexistente() {
        MedicaoLinha leitura = leitura();
        leitura.setFonte(entityManager.getReference(FonteMedicao.class, Long.MAX_VALUE));
        violacao(leitura, "23506", "fk_medicao_fonte");
    }

    private MedicaoLinha leitura() {
        FonteMedicao fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        MedicaoLinha leitura = new MedicaoLinha();
        leitura.setFonte(fonte);
        leitura.setMedidoEm(OffsetDateTime.parse("2026-01-01T10:00:00.123456-03:00"));
        leitura.setRecebidoEm(OffsetDateTime.parse("2026-01-01T13:05:00.654321Z"));
        leitura.setTensaoKv(new BigDecimal("138.123456"));
        leitura.setCorrenteA(new BigDecimal("50.654321"));
        return leitura;
    }

    private void violacao(MedicaoLinha leitura, String estado, String constraint) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(leitura));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(estado);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }

    private LinhaTransmissao linha() {
        Subestacao a = new Subestacao();
        a.setNome("Origem");
        a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(a);
        Subestacao b = new Subestacao();
        b.setNome("Destino");
        b.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(b);
        LinhaTransmissao linha = new LinhaTransmissao();
        linha.setNome("Linha");
        linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setSubestacaoOrigem(a);
        linha.setSubestacaoDestino(b);
        linha.setExtensaoKm(new BigDecimal("100"));
        linha.setTensaoOperacaoKv(new BigDecimal("138"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

    private FonteMedicao fonte() {
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador("SENSOR-01");
        fonte.setLinha(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }
}
