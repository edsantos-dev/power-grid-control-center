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
class AlertaComunicacaoRepositoryTest {
    @Autowired private AlertaComunicacaoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void devePersistirAlertaAtivoComSnapshotsERelacionamentosLazy() {
        var alerta = repository.saveAndFlush(novo());
        Long fonteId = alerta.getFonte().getId();
        Long ativoId = alerta.getAtivo().getId();
        entityManager.clear();
        var salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusComunicacao.ATIVO);
        assertThat(salvo.getCriticidade()).isEqualTo(Criticidade.MEDIA);
        assertThat(salvo.getRestabelecidoEm()).isNull();
        assertThat(salvo.getToleranciaSegundos()).isEqualTo(300);
        assertThat(salvo.getMonitoradaDesde().toInstant()).isEqualTo(alerta.getMonitoradaDesde().toInstant());
        assertThat(salvo.getUltimaComunicacaoEm().toInstant()).isEqualTo(alerta.getUltimaComunicacaoEm().toInstant());
        assertThat(salvo.getDetectadoEm().toInstant()).isEqualTo(alerta.getDetectadoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getFonte())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getAtivo())).isFalse();
        assertThat(salvo.getFonte().getId()).isEqualTo(fonteId);
        assertThat(salvo.getAtivo().getId()).isEqualTo(ativoId);
    }

    @Test
    void devePersistirRestabelecidoComInstante() {
        var alerta = novo();
        alerta.setStatus(StatusComunicacao.RESTABELECIDO);
        alerta.setRestabelecidoEm(alerta.getDetectadoEm().plusMinutes(1));
        repository.saveAndFlush(alerta);
        entityManager.clear();
        var salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusComunicacao.RESTABELECIDO);
        assertThat(salvo.getRestabelecidoEm().toInstant()).isEqualTo(alerta.getRestabelecidoEm().toInstant());
    }

    @Test
    void deveAceitarFonteQueNuncaComunicou() {
        var alerta = novo(); alerta.setUltimaComunicacaoEm(null);
        repository.saveAndFlush(alerta); entityManager.clear();
        assertThat(repository.findById(alerta.getId()).orElseThrow().getUltimaComunicacaoEm()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATIVO", "RESTABELECIDO"})
    void deveRejeitarCicloInconsistente(String status) {
        var alerta = novo(); alerta.setStatus(StatusComunicacao.valueOf(status));
        if (status.equals("ATIVO")) alerta.setRestabelecidoEm(alerta.getDetectadoEm());
        violacao(alerta, "23513", "ck_comunicacao_ciclo");
    }

    @Test
    void deveRejeitarStatusDesconhecidoNoBanco() {
        var alerta = repository.saveAndFlush(novo());
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE alerta_comunicacao SET status = 'OUTRO' WHERE id = ?", alerta.getId()));
        conferir(ex, "23513", "ck_comunicacao_ciclo");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarToleranciaNaoPositiva(int valor) {
        var alerta = novo(); alerta.setToleranciaSegundos(valor);
        violacao(alerta, "23513", "ck_comunicacao_tolerancia");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte_id", "ativo_id", "monitorada_desde", "tolerancia_segundos", "detectado_em", "status"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        var alerta = novo();
        switch (campo) {
            case "fonte_id" -> alerta.setFonte(null);
            case "ativo_id" -> alerta.setAtivo(null);
            case "monitorada_desde" -> alerta.setMonitoradaDesde(null);
            case "tolerancia_segundos" -> alerta.setToleranciaSegundos(null);
            case "detectado_em" -> alerta.setDetectadoEm(null);
            default -> alerta.setStatus(null);
        }
        violacao(alerta, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte", "ativo"})
    void deveRejeitarReferenciaInexistente(String campo) {
        var alerta = novo();
        if (campo.equals("fonte")) alerta.setFonte(entityManager.getReference(FonteMedicao.class, Long.MAX_VALUE));
        else alerta.setAtivo(entityManager.getReference(AtivoRede.class, Long.MAX_VALUE));
        violacao(alerta, "23506", "fk_comunicacao_" + campo);
    }

    private AlertaComunicacao novo() {
        var fonte = fonte();
        fonte.setMonitoradaDesde(OffsetDateTime.parse("2026-10-30T10:00:00Z"));
        fonte.setUltimaComunicacaoEm(OffsetDateTime.parse("2026-10-30T11:00:00Z"));
        entityManager.persist(fonte); entityManager.flush();
        var alerta = new AlertaComunicacao();
        alerta.setFonte(fonte); alerta.setAtivo(fonte.getAtivo());
        alerta.setMonitoradaDesde(fonte.getMonitoradaDesde());
        alerta.setUltimaComunicacaoEm(fonte.getUltimaComunicacaoEm());
        alerta.setToleranciaSegundos(300);
        alerta.setDetectadoEm(OffsetDateTime.parse("2026-10-30T11:05:01Z"));
        alerta.setStatus(StatusComunicacao.ATIVO);
        return alerta;
    }

    private void violacao(AlertaComunicacao alerta, String state, String constraint) {
        conferir(assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(alerta)), state, constraint);
    }

    private void conferir(DataIntegrityViolationException ex, String state, String constraint) {
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(state);
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
        fonte.setAtivo(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }
}
