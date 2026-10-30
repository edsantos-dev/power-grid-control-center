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
class FonteMedicaoRepositoryTest {
    @Autowired private FonteMedicaoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(OrigemMedicao.class)
    void devePersistirOrigemEVinculoLazy(OrigemMedicao origem) {
        FonteMedicao fonte = fonte();
        fonte.setOrigem(origem);
        repository.saveAndFlush(fonte);
        Long linhaId = fonte.getAtivo().getId();
        entityManager.clear();
        FonteMedicao salvo = repository.findById(fonte.getId()).orElseThrow();
        assertThat(salvo.getIdentificador()).isEqualTo("SENSOR-01");
        assertThat(salvo.getOrigem()).isEqualTo(origem);
        assertThat(Hibernate.isInitialized(salvo.getAtivo())).isFalse();
        assertThat(salvo.getAtivo().getId()).isEqualTo(linhaId);
    }

    @Test
    void deveRejeitarIdentificadorDuplicado() {
        FonteMedicao fonte = repository.saveAndFlush(fonte());
        FonteMedicao duplicada = new FonteMedicao();
        duplicada.setIdentificador(fonte.getIdentificador());
        duplicada.setAtivo(fonte.getAtivo());
        duplicada.setOrigem(OrigemMedicao.SIMULADA);
        violacao(duplicada, "23505");
    }

    @ParameterizedTest
    @ValueSource(strings = {"identificador", "origem", "linha"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        FonteMedicao fonte = fonte();
        switch (campo) {
            case "identificador" -> fonte.setIdentificador(null);
            case "origem" -> fonte.setOrigem(null);
            default -> fonte.setAtivo(null);
        }
        violacao(fonte, "23502");
    }

    @Test
    void deveRejeitarLinhaInexistente() {
        FonteMedicao fonte = fonte();
        fonte.setAtivo(entityManager.getReference(LinhaTransmissao.class, Long.MAX_VALUE));
        violacao(fonte, "23506");
    }

    @Test
    void deveRejeitarOrigemForaDoEnumNoBanco() {
        Long linhaId = linha().getId();
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO fonte_medicao (identificador, ativo_id, origem) VALUES (?, ?, ?)",
                        "INVALIDO", linhaId, "OUTRA"));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo("23513"));
    }

    @Test
    void devePersistirReferenciaDeSubestacaoLazy() {
        FonteMedicao fonte = fonte();
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoOrigem());
        Long referenciaId = fonte.getSubestacaoReferencia().getId();
        repository.saveAndFlush(fonte);
        entityManager.clear();
        FonteMedicao salvo = repository.findById(fonte.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(salvo.getSubestacaoReferencia())).isFalse();
        assertThat(salvo.getSubestacaoReferencia().getId()).isEqualTo(referenciaId);
        assertThat(salvo.getSubestacaoReferencia().getNome()).isEqualTo("Origem");
    }

    @Test
    void devePreservarFonteSemReferencia() {
        FonteMedicao fonte = repository.saveAndFlush(fonte());
        entityManager.clear();
        assertThat(repository.findById(fonte.getId()).orElseThrow().getSubestacaoReferencia()).isNull();
    }

    @Test
    void deveRejeitarReferenciaInexistente() {
        FonteMedicao fonte = fonte();
        fonte.setSubestacaoReferencia(entityManager.getReference(Subestacao.class, Long.MAX_VALUE));
        violacao(fonte, "23506");
    }

    private void violacao(FonteMedicao fonte, String estado) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(fonte));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo(estado));
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
        fonte.setAtivo(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }
}
