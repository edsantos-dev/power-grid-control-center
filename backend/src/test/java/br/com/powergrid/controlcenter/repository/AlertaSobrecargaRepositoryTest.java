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
class AlertaSobrecargaRepositoryTest {
    @Autowired private AlertaSobrecargaRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void devePersistirAtivoSemReconhecimento() {
        AlertaSobrecarga alerta = novo(); repository.saveAndFlush(alerta);
        Long ativoId = alerta.getAtivo().getId(); Long medicaoId = alerta.getMedicao().getId(); entityManager.clear();
        AlertaSobrecarga salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.ATIVO);
        assertThat(salvo.getCriticidade()).isEqualTo(Criticidade.ALTA);
        assertThat(salvo.getReconhecidoEm()).isNull(); assertThat(salvo.getReconhecidoPorUsuario()).isNull();
        assertThat(salvo.getCargaPercentual()).isEqualByComparingTo("108");
        assertThat(salvo.getLimiarPercentual()).isEqualByComparingTo("100");
        assertThat(salvo.getGeradoEm().toInstant()).isEqualTo(alerta.getGeradoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getAtivo())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getMedicao())).isFalse();
        assertThat(salvo.getAtivo().getId()).isEqualTo(ativoId);
        assertThat(salvo.getMedicao().getId()).isEqualTo(medicaoId);
    }

    @Test
    void devePersistirReconhecidoComAutorEInstante() {
        AlertaSobrecarga alerta = novo(); alerta.setStatus(StatusAlerta.RECONHECIDO);
        alerta.setReconhecidoPorUsuario(usuario()); alerta.setReconhecidoEm(alerta.getGeradoEm().plusMinutes(1));
        repository.saveAndFlush(alerta); Long usuarioId = alerta.getReconhecidoPorUsuario().getId(); entityManager.clear();
        AlertaSobrecarga salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.RECONHECIDO);
        assertThat(salvo.getReconhecidoEm().toInstant()).isEqualTo(alerta.getReconhecidoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getReconhecidoPorUsuario())).isFalse();
        assertThat(salvo.getReconhecidoPorUsuario().getId()).isEqualTo(usuarioId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoUsuario", "ativoInstante", "ativoAmbos", "reconhecidoNenhum", "reconhecidoSemAutor", "reconhecidoSemInstante"})
    void deveRejeitarCicloInconsistente(String caso) {
        AlertaSobrecarga alerta = novo();
        if (caso.startsWith("reconhecido")) alerta.setStatus(StatusAlerta.RECONHECIDO);
        if (caso.equals("ativoUsuario") || caso.equals("ativoAmbos") || caso.equals("reconhecidoSemInstante")) alerta.setReconhecidoPorUsuario(usuario());
        if (caso.equals("ativoInstante") || caso.equals("ativoAmbos") || caso.equals("reconhecidoSemAutor")) alerta.setReconhecidoEm(alerta.getGeradoEm());
        violacao(alerta, "23513", "ck_sobrecarga_ciclo");
    }

    @Test
    void deveRejeitarSegundoAlertaParaMesmaMedicao() {
        AlertaSobrecarga primeiro = repository.saveAndFlush(novo());
        AlertaSobrecarga segundo = new AlertaSobrecarga();
        segundo.setAtivo(primeiro.getAtivo()); segundo.setMedicao(primeiro.getMedicao());
        segundo.setCargaPercentual(primeiro.getCargaPercentual()); segundo.setLimiarPercentual(primeiro.getLimiarPercentual());
        segundo.setGeradoEm(primeiro.getGeradoEm()); segundo.setStatus(StatusAlerta.ATIVO);
        violacao(segundo, "23505", "uq_sobrecarga_medicao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void deveRejeitarLimiarNaoPositivo(String valor) {
        AlertaSobrecarga alerta = novo(); alerta.setLimiarPercentual(new BigDecimal(valor));
        violacao(alerta, "23513", "ck_sobrecarga_limiar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo_id", "medicao_id", "carga_percentual", "limiar_percentual", "status", "gerado_em"})
    void deveRejeitarNulosObrigatorios(String campo) {
        AlertaSobrecarga alerta = novo();
        switch (campo) {
            case "ativo_id" -> alerta.setAtivo(null);
            case "medicao_id" -> alerta.setMedicao(null);
            case "carga_percentual" -> alerta.setCargaPercentual(null);
            case "limiar_percentual" -> alerta.setLimiarPercentual(null);
            case "status" -> alerta.setStatus(null);
            default -> alerta.setGeradoEm(null);
        }
        violacao(alerta, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo", "medicao", "usuario"})
    void deveRejeitarReferenciasInexistentes(String referencia) {
        AlertaSobrecarga alerta = novo();
        switch (referencia) {
            case "ativo" -> alerta.setAtivo(entityManager.getReference(AtivoRede.class, Long.MAX_VALUE));
            case "medicao" -> alerta.setMedicao(entityManager.getReference(MedicaoTransformador.class, Long.MAX_VALUE));
            default -> {
                alerta.setStatus(StatusAlerta.RECONHECIDO); alerta.setReconhecidoEm(alerta.getGeradoEm());
                alerta.setReconhecidoPorUsuario(entityManager.getReference(Usuario.class, Long.MAX_VALUE));
            }
        }
        violacao(alerta, "23506", "fk_sobrecarga_" + referencia);
    }

    @Test
    void deveRejeitarStatusDesconhecidoNoBanco() {
        AlertaSobrecarga alerta = novo();
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO alerta_sobrecarga (ativo_id, medicao_id, carga_percentual, limiar_percentual, status, gerado_em) VALUES (?, ?, 108, 100, 'OUTRO', ?)",
                alerta.getAtivo().getId(), alerta.getMedicao().getId(), alerta.getGeradoEm()));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo("23513");
            assertThat(cause.getMessage()).containsIgnoringCase("ck_sobrecarga_ciclo");
        });
    }

    private AlertaSobrecarga novo() {
        MedicaoTransformador medicao = leitura(); entityManager.persist(medicao); entityManager.flush();
        AlertaSobrecarga alerta = new AlertaSobrecarga(); alerta.setAtivo(medicao.getFonte().getAtivo());
        alerta.setMedicao(medicao); alerta.setCargaPercentual(new BigDecimal("108.000000"));
        alerta.setLimiarPercentual(new BigDecimal("100.000000")); alerta.setStatus(StatusAlerta.ATIVO);
        alerta.setGeradoEm(OffsetDateTime.parse("2026-10-30T12:00:10Z")); return alerta;
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario(); usuario.setNome("Operador"); usuario.setEmail("reconhecimento@teste.local");
        usuario.setMatricula("RECONHECIMENTO-TESTE"); usuario.setPerfil(PerfilAcesso.OPERADOR);
        entityManager.persist(usuario); entityManager.flush(); return usuario;
    }

    private void violacao(AlertaSobrecarga alerta, String sqlState, String constraint) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(alerta));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
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
        m.setPotenciaAparenteMva(new BigDecimal("10.8"));
        m.setCapacidadeReferenciaMva(new BigDecimal("10")); return m;
    }

}
