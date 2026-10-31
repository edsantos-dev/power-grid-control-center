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
import org.junit.jupiter.params.provider.CsvSource;
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
class AlertaTensaoRepositoryTest {
    @Autowired private AlertaTensaoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void devePersistirAtivoSemReconhecimento() {
        AlertaTensao alerta = novo(); repository.saveAndFlush(alerta);
        Long ativoId = alerta.getAtivo().getId(); Long medicaoId = alerta.getMedicao().getId(); entityManager.clear();
        AlertaTensao salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.ATIVO);
        assertThat(salvo.getReconhecidoEm()).isNull(); assertThat(salvo.getReconhecidoPorUsuario()).isNull();
        assertThat(salvo.getTensaoMedidaKv()).isEqualByComparingTo("90");
        assertThat(salvo.getTensaoReferenciaKv()).isEqualByComparingTo("100");
        assertThat(salvo.getGeradoEm().toInstant()).isEqualTo(alerta.getGeradoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getAtivo())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getMedicao())).isFalse();
        assertThat(salvo.getAtivo().getId()).isEqualTo(ativoId);
        assertThat(salvo.getMedicao().getId()).isEqualTo(medicaoId);
    }

    @Test
    void devePersistirReconhecidoComAutorEInstante() {
        AlertaTensao alerta = novo(); alerta.setStatus(StatusAlerta.RECONHECIDO);
        alerta.setReconhecidoPorUsuario(usuario()); alerta.setReconhecidoEm(alerta.getGeradoEm().plusMinutes(1));
        repository.saveAndFlush(alerta); Long usuarioId = alerta.getReconhecidoPorUsuario().getId(); entityManager.clear();
        AlertaTensao salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.RECONHECIDO);
        assertThat(salvo.getReconhecidoEm().toInstant()).isEqualTo(alerta.getReconhecidoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getReconhecidoPorUsuario())).isFalse();
        assertThat(salvo.getReconhecidoPorUsuario().getId()).isEqualTo(usuarioId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoUsuario", "ativoInstante", "ativoAmbos", "reconhecidoNenhum", "reconhecidoSemAutor", "reconhecidoSemInstante"})
    void deveRejeitarCicloInconsistente(String caso) {
        AlertaTensao alerta = novo();
        if (caso.startsWith("reconhecido")) alerta.setStatus(StatusAlerta.RECONHECIDO);
        if (caso.equals("ativoUsuario") || caso.equals("ativoAmbos") || caso.equals("reconhecidoSemInstante")) alerta.setReconhecidoPorUsuario(usuario());
        if (caso.equals("ativoInstante") || caso.equals("ativoAmbos") || caso.equals("reconhecidoSemAutor")) alerta.setReconhecidoEm(alerta.getGeradoEm());
        violacao(alerta, "23513", "ck_tensao_ciclo");
    }

    @Test
    void deveRejeitarSegundoAlertaParaMesmaMedicao() {
        AlertaTensao primeiro = repository.saveAndFlush(novo());
        AlertaTensao segundo = new AlertaTensao();
        segundo.setAtivo(primeiro.getAtivo()); segundo.setMedicao(primeiro.getMedicao());
        segundo.setTensaoMedidaKv(primeiro.getTensaoMedidaKv()); segundo.setTensaoReferenciaKv(primeiro.getTensaoReferenciaKv());
        segundo.setTipo(primeiro.getTipo());
        segundo.setLimiteInferiorKv(primeiro.getLimiteInferiorKv());
        segundo.setLimiteSuperiorKv(primeiro.getLimiteSuperiorKv());
        segundo.setGeradoEm(primeiro.getGeradoEm()); segundo.setStatus(StatusAlerta.ATIVO);
        violacao(segundo, "23505", "uq_tensao_medicao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void deveRejeitarReferenciaNaoPositiva(String valor) {
        AlertaTensao alerta = novo(); alerta.setTensaoReferenciaKv(new BigDecimal(valor));
        violacao(alerta, "23513", "ck_tensao_referencia");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo_id", "medicao_id", "tensao_medida_kv", "tensao_referencia_kv", "limite_inferior_kv", "limite_superior_kv", "tipo", "status", "gerado_em"})
    void deveRejeitarNulosObrigatorios(String campo) {
        AlertaTensao alerta = novo();
        switch (campo) {
            case "ativo_id" -> alerta.setAtivo(null);
            case "medicao_id" -> alerta.setMedicao(null);
            case "tensao_medida_kv" -> alerta.setTensaoMedidaKv(null);
            case "tensao_referencia_kv" -> alerta.setTensaoReferenciaKv(null);
            case "limite_inferior_kv" -> alerta.setLimiteInferiorKv(null);
            case "limite_superior_kv" -> alerta.setLimiteSuperiorKv(null);
            case "tipo" -> alerta.setTipo(null);
            case "status" -> alerta.setStatus(null);
            default -> alerta.setGeradoEm(null);
        }
        violacao(alerta, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo", "medicao", "usuario"})
    void deveRejeitarReferenciasInexistentes(String referencia) {
        AlertaTensao alerta = novo();
        switch (referencia) {
            case "ativo" -> alerta.setAtivo(entityManager.getReference(AtivoRede.class, Long.MAX_VALUE));
            case "medicao" -> alerta.setMedicao(entityManager.getReference(MedicaoLinha.class, Long.MAX_VALUE));
            default -> {
                alerta.setStatus(StatusAlerta.RECONHECIDO); alerta.setReconhecidoEm(alerta.getGeradoEm());
                alerta.setReconhecidoPorUsuario(entityManager.getReference(Usuario.class, Long.MAX_VALUE));
            }
        }
        violacao(alerta, "23506", "fk_tensao_" + referencia);
    }

    @Test
    void deveRejeitarStatusDesconhecidoNoBanco() {
        AlertaTensao alerta = novo();
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO alerta_tensao (ativo_id, medicao_id, tipo, tensao_medida_kv, tensao_referencia_kv, limite_inferior_kv, limite_superior_kv, status, gerado_em) VALUES (?, ?, 'SUBTENSAO', 90, 100, 95, 105, 'OUTRO', ?)",
                alerta.getAtivo().getId(), alerta.getMedicao().getId(), alerta.getGeradoEm()));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo("23513");
            assertThat(cause.getMessage()).containsIgnoringCase("ck_tensao_ciclo");
        });
    }

    @ParameterizedTest
    @EnumSource(TipoAlertaTensao.class)
    void devePersistirTiposEValoresDosSnapshots(TipoAlertaTensao tipo) {
        AlertaTensao alerta = novo(); alerta.setTipo(tipo);
        BigDecimal medida = new BigDecimal(tipo == TipoAlertaTensao.SUBTENSAO ? "0" : "110");
        alerta.setTensaoMedidaKv(medida); alerta.getMedicao().setTensaoKv(medida);
        repository.saveAndFlush(alerta); entityManager.clear();
        AlertaTensao salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getTipo()).isEqualTo(tipo);
        assertThat(salvo.getTensaoMedidaKv()).isEqualByComparingTo(medida);
        assertThat(salvo.getLimiteInferiorKv()).isEqualByComparingTo("95");
        assertThat(salvo.getLimiteSuperiorKv()).isEqualByComparingTo("105");
    }

    @ParameterizedTest
    @CsvSource({"inferior,0", "inferior,-1", "superior,0", "superior,-1"})
    void deveRejeitarLimitesNaoPositivos(String campo, String valor) {
        AlertaTensao alerta = novo();
        if (campo.equals("inferior")) alerta.setLimiteInferiorKv(new BigDecimal(valor));
        else alerta.setLimiteSuperiorKv(new BigDecimal(valor));
        violacao(alerta, "23513", "ck_tensao_limite_" + campo);
    }

    @Test
    void deveRejeitarTensaoMedidaNegativa() {
        AlertaTensao alerta = novo(); alerta.setTensaoMedidaKv(new BigDecimal("-0.000001"));
        violacao(alerta, "23513", "ck_tensao_medida");
    }

    @Test
    void deveRejeitarTipoDesconhecidoNoBanco() {
        AlertaTensao alerta = novo();
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO alerta_tensao (ativo_id, medicao_id, tipo, tensao_medida_kv, tensao_referencia_kv, limite_inferior_kv, limite_superior_kv, status, gerado_em) VALUES (?, ?, 'OUTRO', 90, 100, 95, 105, 'ATIVO', ?)",
                alerta.getAtivo().getId(), alerta.getMedicao().getId(), alerta.getGeradoEm()));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo("23513");
            assertThat(cause.getMessage()).containsIgnoringCase("ck_tensao_tipo");
        });
    }

    @Test
    void devePreservarOitoCasasDosLimites() {
        AlertaTensao alerta = novo();
        alerta.setLimiteInferiorKv(new BigDecimal("131.21728395"));
        alerta.setLimiteSuperiorKv(new BigDecimal("145.02962963"));
        repository.saveAndFlush(alerta); entityManager.clear();
        AlertaTensao salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getLimiteInferiorKv()).isEqualByComparingTo("131.21728395");
        assertThat(salvo.getLimiteSuperiorKv()).isEqualByComparingTo("145.02962963");
    }

    private AlertaTensao novo() {
        MedicaoLinha medicao = leitura(); entityManager.persist(medicao); entityManager.flush();
        AlertaTensao alerta = new AlertaTensao(); alerta.setAtivo(medicao.getFonte().getAtivo());
        alerta.setMedicao(medicao); alerta.setTipo(TipoAlertaTensao.SUBTENSAO);
        alerta.setTensaoMedidaKv(new BigDecimal("90.000000"));
        alerta.setTensaoReferenciaKv(new BigDecimal("100.000000"));
        alerta.setLimiteInferiorKv(new BigDecimal("95.00000000"));
        alerta.setLimiteSuperiorKv(new BigDecimal("105.00000000"));
        alerta.setStatus(StatusAlerta.ATIVO);
        alerta.setGeradoEm(OffsetDateTime.parse("2026-10-30T12:00:10Z")); return alerta;
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario(); usuario.setNome("Operador"); usuario.setEmail("reconhecimento@teste.local");
        usuario.setMatricula("RECONHECIMENTO-TESTE"); usuario.setPerfil(PerfilAcesso.OPERADOR);
        entityManager.persist(usuario); entityManager.flush(); return usuario;
    }

    private void violacao(AlertaTensao alerta, String sqlState, String constraint) {
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(alerta));
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
    private MedicaoLinha leitura() {
        FonteMedicao fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        MedicaoLinha leitura = new MedicaoLinha();
        leitura.setFonte(fonte);
        leitura.setMedidoEm(OffsetDateTime.parse("2026-01-01T10:00:00.123456-03:00"));
        leitura.setRecebidoEm(OffsetDateTime.parse("2026-01-01T13:05:00.654321Z"));
        leitura.setTensaoKv(new BigDecimal("90"));
        leitura.setCorrenteA(new BigDecimal("50.654321"));
        return leitura;
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
        linha.setTensaoOperacaoKv(new BigDecimal("100"));
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
