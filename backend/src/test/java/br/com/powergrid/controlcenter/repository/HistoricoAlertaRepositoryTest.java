package br.com.powergrid.controlcenter.repository;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class HistoricoAlertaRepositoryTest {
    @Autowired private HistoricoAlertaRepository repository;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"ABERTO,RECONHECIDO", "RECONHECIDO,RESOLVIDO"})
    void devePersistirTransicoesPermitidasERecuperarAssociacoesLazy(SituacaoAlerta anterior, SituacaoAlerta nova) {
        var h = novo(); h.setSituacaoAnterior(anterior); h.setSituacaoNova(nova);
        repository.saveAndFlush(h);
        Long alertaId = h.getAlerta().getId(); Long usuarioId = h.getUsuario().getId(); em.clear();
        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getSituacaoAnterior()).isEqualTo(anterior);
        assertThat(salvo.getSituacaoNova()).isEqualTo(nova);
        assertThat(salvo.getComentario()).isEqualTo("Tratativa registrada pelo operador.");
        assertThat(salvo.getAlteradoEm().toInstant()).isEqualTo(h.getAlteradoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getAlerta())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getUsuario())).isFalse();
        assertThat(salvo.getAlerta().getId()).isEqualTo(alertaId);
        assertThat(salvo.getUsuario().getId()).isEqualTo(usuarioId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alerta_id", "usuario_id", "situacao_anterior", "situacao_nova", "alterado_em", "comentario"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        var h = novo();
        switch (campo) {
            case "alerta_id" -> h.setAlerta(null);
            case "usuario_id" -> h.setUsuario(null);
            case "situacao_anterior" -> h.setSituacaoAnterior(null);
            case "situacao_nova" -> h.setSituacaoNova(null);
            case "alterado_em" -> h.setAlteradoEm(null);
            default -> h.setComentario(null);
        }
        violacao(h, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alerta", "usuario"})
    void deveRejeitarReferenciasInexistentes(String campo) {
        var h = novo();
        if (campo.equals("alerta")) h.setAlerta(em.getReference(Alerta.class, Long.MAX_VALUE));
        else h.setUsuario(em.getReference(Usuario.class, Long.MAX_VALUE));
        violacao(h, "23506", campo.equals("alerta") ? "fk_historico_alerta" : "fk_historico_alerta_usuario");
    }

    @Test
    void deveAceitarComentarioComMilCaracteres() {
        var h = novo(); h.setComentario("a".repeat(1000)); repository.saveAndFlush(h); em.clear();
        assertThat(repository.findById(h.getId()).orElseThrow().getComentario()).hasSize(1000);
    }

    @Test
    void deveRejeitarComentarioAcimaDeMilCaracteres() {
        var h = novo(); h.setComentario("a".repeat(1001));
        violacao(h, "22001", "comentario");
    }

    @ParameterizedTest
    @CsvSource({"ABERTO,ABERTO", "ABERTO,RESOLVIDO", "RECONHECIDO,ABERTO", "RECONHECIDO,RECONHECIDO",
            "RESOLVIDO,ABERTO", "RESOLVIDO,RECONHECIDO", "RESOLVIDO,RESOLVIDO"})
    void deveBloquearSaltoDiretoRetornosERepeticoes(SituacaoAlerta anterior, SituacaoAlerta nova) {
        var h = novo(); h.setSituacaoAnterior(anterior); h.setSituacaoNova(nova);
        violacao(h, "23513", "ck_historico_alerta_transicao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"situacao_anterior", "situacao_nova"})
    void deveRejeitarEstadosDesconhecidosPorSqlDireto(String coluna) {
        var h = repository.saveAndFlush(novo());
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE historico_alerta SET " + coluna + " = 'OUTRO' WHERE id = ?", h.getId()));
        conferir(ex, "23513", "ck_historico_alerta_transicao");
    }

    @ParameterizedTest
    @CsvSource({"ABERTO,RECONHECIDO", "RECONHECIDO,RESOLVIDO"})
    void deveBloquearMesmoDestinoDuplicadoNoMesmoAlerta(SituacaoAlerta anterior, SituacaoAlerta nova) {
        var primeiro = novo(); primeiro.setSituacaoAnterior(anterior); primeiro.setSituacaoNova(nova);
        repository.saveAndFlush(primeiro);
        var repetido = historico(primeiro.getAlerta(), primeiro.getUsuario(), anterior, nova, primeiro.getAlteradoEm().plusMinutes(1));
        violacao(repetido, "23505", "uq_historico_alerta_destino");
    }

    @Test
    void devePermitirMesmoDestinoEmAlertasDistintos() {
        var a = repository.saveAndFlush(novo()); var b = repository.saveAndFlush(novo());
        assertThat(a.getAlerta().getId()).isNotEqualTo(b.getAlerta().getId());
        assertThat(a.getSituacaoNova()).isEqualTo(b.getSituacaoNova());
        assertThat(repository.countByAlertaId(a.getAlerta().getId())).isEqualTo(1);
        assertThat(repository.countByAlertaId(b.getAlerta().getId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePaginarPorInstanteDecrescenteDesempatarPorIdEIsolarAlerta(boolean empate) {
        var reconhecimento = novo();
        var resolucao = historico(reconhecimento.getAlerta(), reconhecimento.getUsuario(), SituacaoAlerta.RECONHECIDO,
                SituacaoAlerta.RESOLVIDO, empate ? reconhecimento.getAlteradoEm() : reconhecimento.getAlteradoEm().plusHours(1));
        if (empate) { repository.saveAndFlush(reconhecimento); repository.saveAndFlush(resolucao); }
        else { repository.saveAndFlush(resolucao); repository.saveAndFlush(reconhecimento); }
        var outro = novo(); outro.setAlteradoEm(reconhecimento.getAlteradoEm().plusDays(1)); repository.saveAndFlush(outro);
        Long alertaId = reconhecimento.getAlerta().getId();
        Long outroAlertaId = outro.getAlerta().getId(); em.clear();

        var primeira = repository.findByAlertaIdOrderByAlteradoEmDescIdDesc(alertaId, PageRequest.of(0, 1));
        assertThat(primeira.getTotalElements()).isEqualTo(2); assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(primeira.isFirst()).isTrue(); assertThat(primeira.isLast()).isFalse();
        assertThat(primeira.getContent()).extracting(HistoricoAlerta::getId).containsExactly(resolucao.getId());
        var segunda = repository.findByAlertaIdOrderByAlteradoEmDescIdDesc(alertaId, PageRequest.of(1, 1));
        assertThat(segunda.isLast()).isTrue();
        assertThat(segunda.getContent()).extracting(HistoricoAlerta::getId).containsExactly(reconhecimento.getId());
        assertThat(repository.findByAlertaIdOrderByAlteradoEmDescIdDesc(alertaId, PageRequest.of(2, 1))).isEmpty();
        assertThat(repository.findByAlertaIdOrderByAlteradoEmDescIdDesc(Long.MAX_VALUE, PageRequest.of(0, 20))).isEmpty();
        assertThat(repository.findByIdAndAlertaId(resolucao.getId(), alertaId)).isPresent();
        assertThat(repository.findByIdAndAlertaId(resolucao.getId(), outroAlertaId)).isEmpty();
        assertThat(repository.buscarPorAlertaOrdenado(alertaId, 1, 0)).extracting(HistoricoAlerta::getId).containsExactly(resolucao.getId());
        assertThat(repository.buscarPorAlertaOrdenado(alertaId, 1, 1)).extracting(HistoricoAlerta::getId).containsExactly(reconhecimento.getId());
        assertThat(repository.buscarPorAlertaOrdenado(alertaId, 1, (long) Integer.MAX_VALUE + 1)).isEmpty();
    }

    @Test
    void alteracoesJpaNaoDevemReescreverAuditoriaExistente() {
        var h = repository.saveAndFlush(novo());
        Long alertaId = h.getAlerta().getId(); Long usuarioId = h.getUsuario().getId();
        var instante = h.getAlteradoEm().toInstant();
        var outroAlerta = novo().getAlerta();
        var outroUsuario = em.createQuery("select u from Usuario u where u.id <> :id", Usuario.class)
                .setParameter("id", usuarioId).setMaxResults(1).getSingleResult();
        h.setAlerta(outroAlerta); h.setUsuario(outroUsuario);
        h.setSituacaoAnterior(SituacaoAlerta.RESOLVIDO); h.setSituacaoNova(SituacaoAlerta.ABERTO);
        h.setAlteradoEm(h.getAlteradoEm().plusDays(1)); h.setComentario("Tentativa de alteração");
        em.flush(); em.clear();
        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getAlerta().getId()).isEqualTo(alertaId);
        assertThat(salvo.getUsuario().getId()).isEqualTo(usuarioId);
        assertThat(salvo.getSituacaoAnterior()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(salvo.getSituacaoNova()).isEqualTo(SituacaoAlerta.RECONHECIDO);
        assertThat(salvo.getAlteradoEm().toInstant()).isEqualTo(instante);
        assertThat(salvo.getComentario()).isEqualTo("Tratativa registrada pelo operador.");
    }

    @Test
    void mudancaDaSituacaoAtualNaoDeveAlterarTransicaoJaAuditada() {
        var h = repository.saveAndFlush(novo());
        h.getAlerta().setSituacao(SituacaoAlerta.RESOLVIDO);
        em.flush(); em.clear();
        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getAlerta().getSituacao()).isEqualTo(SituacaoAlerta.RESOLVIDO);
        assertThat(salvo.getSituacaoAnterior()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(salvo.getSituacaoNova()).isEqualTo(SituacaoAlerta.RECONHECIDO);
        assertThat(repository.countByAlertaId(salvo.getAlerta().getId())).isEqualTo(1);
    }

    private HistoricoAlerta novo() {
        var alerta = new Alerta(); alerta.setTipo("Ocorrência operacional");
        alerta.setCriticidade(Criticidade.ALTA); alerta.setEscopo(EscopoAlerta.REDE_MONITORADA);
        alerta.setSituacao(SituacaoAlerta.ABERTO); alerta.setGeradoEm(OffsetDateTime.parse("2026-11-03T09:00:00Z"));
        em.persist(alerta);
        var usuario = em.createQuery("select u from Usuario u order by u.id", Usuario.class).setMaxResults(1).getSingleResult();
        return historico(alerta, usuario, SituacaoAlerta.ABERTO, SituacaoAlerta.RECONHECIDO,
                OffsetDateTime.parse("2026-11-03T10:00:00.123456-03:00"));
    }

    private HistoricoAlerta historico(Alerta alerta, Usuario usuario, SituacaoAlerta anterior,
            SituacaoAlerta nova, OffsetDateTime instante) {
        var h = new HistoricoAlerta(); h.setAlerta(alerta); h.setUsuario(usuario);
        h.setSituacaoAnterior(anterior); h.setSituacaoNova(nova);
        h.setAlteradoEm(instante); h.setComentario("Tratativa registrada pelo operador."); return h;
    }

    private void violacao(HistoricoAlerta h, String state, String mensagem) {
        conferir(assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(h)), state, mensagem);
    }

    private void conferir(DataIntegrityViolationException ex, String state, String mensagem) {
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(state);
            assertThat(cause.getMessage()).containsIgnoringCase(mensagem);
        });
    }
}
