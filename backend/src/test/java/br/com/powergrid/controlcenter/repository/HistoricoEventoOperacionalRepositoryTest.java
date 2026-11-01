package br.com.powergrid.controlcenter.repository;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
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
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class HistoricoEventoOperacionalRepositoryTest {
    @Autowired private HistoricoEventoOperacionalRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"ABERTO,EM_ANDAMENTO", "EM_ANDAMENTO,CONCLUIDO"})
    void devePersistirTransicoesValidasERecuperarAssociacoesLazy(SituacaoEventoOperacional anterior, SituacaoEventoOperacional nova) {
        var historico = novo(); historico.setSituacaoAnterior(anterior); historico.setSituacaoNova(nova);
        repository.saveAndFlush(historico);
        Long eventoId = historico.getEvento().getId(); Long usuarioId = historico.getUsuario().getId(); entityManager.clear();
        var salvo = repository.findById(historico.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getSituacaoAnterior()).isEqualTo(anterior); assertThat(salvo.getSituacaoNova()).isEqualTo(nova);
        assertThat(salvo.getComentario()).isEqualTo("Atendimento iniciado pelo operador.");
        assertThat(salvo.getAlteradoEm().toInstant()).isEqualTo(historico.getAlteradoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getEvento())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getUsuario())).isFalse();
        assertThat(salvo.getEvento().getId()).isEqualTo(eventoId); assertThat(salvo.getUsuario().getId()).isEqualTo(usuarioId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evento_id", "usuario_id", "situacao_anterior", "situacao_nova", "alterado_em", "comentario"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        var historico = novo();
        switch (campo) {
            case "evento_id" -> historico.setEvento(null);
            case "usuario_id" -> historico.setUsuario(null);
            case "situacao_anterior" -> historico.setSituacaoAnterior(null);
            case "situacao_nova" -> historico.setSituacaoNova(null);
            case "alterado_em" -> historico.setAlteradoEm(null);
            default -> historico.setComentario(null);
        }
        violacao(historico, "23502", campo);
    }

    @Test
    void deveAceitarComentarioNoLimiteDeMilCaracteres() {
        var historico = novo(); historico.setComentario("a".repeat(1000)); repository.saveAndFlush(historico); entityManager.clear();
        assertThat(repository.findById(historico.getId()).orElseThrow().getComentario()).hasSize(1000);
    }

    @Test
    void deveRejeitarComentarioAcimaDeMilCaracteres() {
        var historico = novo(); historico.setComentario("a".repeat(1001));
        violacao(historico, "22001", "comentario");
    }

    @ParameterizedTest
    @CsvSource({"ABERTO,ABERTO", "ABERTO,CONCLUIDO", "EM_ANDAMENTO,ABERTO", "EM_ANDAMENTO,EM_ANDAMENTO",
            "CONCLUIDO,ABERTO", "CONCLUIDO,EM_ANDAMENTO", "CONCLUIDO,CONCLUIDO"})
    void deveRejeitarSaltosRetornosERepeticoes(SituacaoEventoOperacional anterior, SituacaoEventoOperacional nova) {
        var historico = novo(); historico.setSituacaoAnterior(anterior); historico.setSituacaoNova(nova);
        violacao(historico, "23513", "ck_historico_evento_transicao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"situacao_anterior", "situacao_nova"})
    void deveRejeitarEstadosDesconhecidosNoBanco(String coluna) {
        var salvo = repository.saveAndFlush(novo());
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE historico_evento_operacional SET " + coluna + " = 'OUTRO' WHERE id = ?", salvo.getId()));
        conferir(ex, "23513", "ck_historico_evento_transicao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"evento", "usuario"})
    void deveRejeitarRelacionamentosInexistentes(String campo) {
        var historico = novo();
        if (campo.equals("evento")) historico.setEvento(entityManager.getReference(EventoOperacional.class, Long.MAX_VALUE));
        else historico.setUsuario(entityManager.getReference(Usuario.class, Long.MAX_VALUE));
        violacao(historico, "23506", campo.equals("evento") ? "fk_historico_evento" : "fk_historico_evento_usuario");
    }

    @Test
    void alteracoesDeRegistroExistenteNaoDevemSobrescreverAuditoria() {
        var salvo = repository.saveAndFlush(novo()); Long id = salvo.getId();
        salvo.setComentario("Tentativa de alteração"); salvo.setAlteradoEm(salvo.getAlteradoEm().plusDays(1));
        entityManager.flush(); entityManager.clear();
        var recuperado = repository.findById(id).orElseThrow();
        assertThat(recuperado.getComentario()).isEqualTo("Atendimento iniciado pelo operador.");
        assertThat(recuperado.getAlteradoEm().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-31T10:00:00.123456-03:00").toInstant());
    }

    @Test
    void deveConsultarHistoricoPaginadoIsoladoPorEvento() {
        var primeiro = repository.saveAndFlush(novo());
        var segundo = new HistoricoEventoOperacional(); segundo.setEvento(primeiro.getEvento()); segundo.setUsuario(primeiro.getUsuario());
        segundo.setSituacaoAnterior(SituacaoEventoOperacional.EM_ANDAMENTO); segundo.setSituacaoNova(SituacaoEventoOperacional.CONCLUIDO);
        segundo.setAlteradoEm(primeiro.getAlteradoEm()); segundo.setComentario("Atendimento concluído."); repository.saveAndFlush(segundo);
        var outroEvento = evento(primeiro.getUsuario());
        var outro = new HistoricoEventoOperacional(); outro.setEvento(outroEvento); outro.setUsuario(primeiro.getUsuario());
        outro.setSituacaoAnterior(SituacaoEventoOperacional.ABERTO); outro.setSituacaoNova(SituacaoEventoOperacional.EM_ANDAMENTO);
        outro.setAlteradoEm(primeiro.getAlteradoEm()); outro.setComentario("Outro atendimento."); repository.saveAndFlush(outro); entityManager.clear();
        var pagina = repository.findByEventoId(primeiro.getEvento().getId(), PageRequest.of(0, 1, Sort.by("alteradoEm", "id")));
        assertThat(pagina.getTotalElements()).isEqualTo(2); assertThat(pagina.getTotalPages()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(HistoricoEventoOperacional::getId).containsExactly(primeiro.getId());
        var segunda = repository.findByEventoId(primeiro.getEvento().getId(), PageRequest.of(1, 1, Sort.by("alteradoEm", "id")));
        assertThat(segunda.getContent()).extracting(HistoricoEventoOperacional::getId).containsExactly(segundo.getId());
        assertThat(repository.findByEventoId(Long.MAX_VALUE, PageRequest.of(0, 20))).isEmpty();
    }

    private HistoricoEventoOperacional novo() {
        var usuario = entityManager.createQuery("select u from Usuario u order by u.id", Usuario.class).setMaxResults(1).getSingleResult();
        var historico = new HistoricoEventoOperacional(); historico.setEvento(evento(usuario)); historico.setUsuario(usuario);
        historico.setSituacaoAnterior(SituacaoEventoOperacional.ABERTO); historico.setSituacaoNova(SituacaoEventoOperacional.EM_ANDAMENTO);
        historico.setAlteradoEm(OffsetDateTime.parse("2026-10-31T10:00:00.123456-03:00"));
        historico.setComentario("Atendimento iniciado pelo operador."); return historico;
    }

    private EventoOperacional evento(Usuario usuario) {
        var sub = new Subestacao(); sub.setNome("Subestação"); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); entityManager.persist(sub);
        var evento = new EventoOperacional(); evento.setAtivo(sub); evento.setCategoria(EventoOperacional.Categoria.MANOBRA);
        evento.setRegistradorUsuario(usuario); evento.setResponsavelUsuario(usuario); evento.setSituacao("ABERTO");
        entityManager.persist(evento); entityManager.flush(); return evento;
    }

    private void violacao(HistoricoEventoOperacional historico, String state, String mensagem) {
        conferir(assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(historico)), state, mensagem);
    }

    private void conferir(DataIntegrityViolationException ex, String state, String mensagem) {
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(state);
            assertThat(cause.getMessage()).containsIgnoringCase(mensagem);
        });
    }
}
