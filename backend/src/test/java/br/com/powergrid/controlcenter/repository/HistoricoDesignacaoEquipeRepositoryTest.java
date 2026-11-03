package br.com.powergrid.controlcenter.repository;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class HistoricoDesignacaoEquipeRepositoryTest {
    @Autowired private HistoricoDesignacaoEquipeRepository repository;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"INCLUSAO", "SUBSTITUICAO", "RETIRADA"})
    void devePersistirHistoricoERecuperarAssociacoesLazy(String operacao) {
        var h = novo();
        if (operacao.equals("INCLUSAO")) h.setEquipeAnterior(null);
        if (operacao.equals("RETIRADA")) h.setEquipeNova(null);
        var evento = h.getEventoOperacional();
        evento.setEquipeDesignada(h.getEquipeNova());
        evento.setVersaoDesignacao(h.getVersaoDesignacao());
        repository.saveAndFlush(h);
        Long eventoId = evento.getId();
        Long anteriorId = h.getEquipeAnterior() == null ? null : h.getEquipeAnterior().getId();
        Long novaId = h.getEquipeNova() == null ? null : h.getEquipeNova().getId();
        Long usuarioId = h.getUsuario().getId();
        em.clear();

        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getComentario()).isEqualTo("Equipe designada pelo operador.");
        assertThat(salvo.getVersaoDesignacao()).isEqualTo(1);
        assertThat(salvo.getRegistradoEm().toInstant()).isEqualTo(h.getRegistradoEm().toInstant());
        assertThat(Hibernate.isInitialized(salvo.getEventoOperacional())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getUsuario())).isFalse();
        if (salvo.getEquipeAnterior() != null) assertThat(Hibernate.isInitialized(salvo.getEquipeAnterior())).isFalse();
        if (salvo.getEquipeNova() != null) assertThat(Hibernate.isInitialized(salvo.getEquipeNova())).isFalse();
        assertThat(salvo.getEventoOperacional().getId()).isEqualTo(eventoId);
        assertThat(salvo.getUsuario().getId()).isEqualTo(usuarioId);
        assertThat(salvo.getEquipeAnterior() == null ? null : salvo.getEquipeAnterior().getId()).isEqualTo(anteriorId);
        assertThat(salvo.getEquipeNova() == null ? null : salvo.getEquipeNova().getId()).isEqualTo(novaId);
        var recuperado = em.find(EventoOperacional.class, eventoId);
        assertThat(recuperado.getVersaoDesignacao()).isEqualTo(1);
        assertThat(recuperado.getEquipeDesignada() == null ? null : recuperado.getEquipeDesignada().getId()).isEqualTo(novaId);
        assertThat(salvo.getUsuario().getId()).isNotEqualTo(recuperado.getResponsavelUsuario().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"evento_operacional_id", "usuario_id", "registrado_em", "comentario", "versao_designacao"})
    void deveRejeitarCamposObrigatoriosNulos(String campo) {
        var h = novo();
        switch (campo) {
            case "evento_operacional_id" -> h.setEventoOperacional(null);
            case "usuario_id" -> h.setUsuario(null);
            case "registrado_em" -> h.setRegistradoEm(null);
            case "comentario" -> h.setComentario(null);
            default -> h.setVersaoDesignacao(null);
        }
        violacao(h, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evento", "usuario", "equipe_anterior", "equipe_nova"})
    void deveRejeitarReferenciasInexistentes(String campo) {
        var h = novo();
        switch (campo) {
            case "evento" -> h.setEventoOperacional(em.getReference(EventoOperacional.class, Long.MAX_VALUE));
            case "usuario" -> h.setUsuario(em.getReference(Usuario.class, Long.MAX_VALUE));
            case "equipe_anterior" -> h.setEquipeAnterior(em.getReference(EquipeManutencao.class, Long.MAX_VALUE));
            default -> h.setEquipeNova(em.getReference(EquipeManutencao.class, Long.MAX_VALUE));
        }
        violacao(h, "23506", "fk_designacao_" + campo);
    }

    @Test
    void deveAceitarComentarioComMilCaracteres() {
        var h = novo(); h.setComentario("a".repeat(1000));
        repository.saveAndFlush(h); em.clear();
        assertThat(repository.findById(h.getId()).orElseThrow().getComentario()).hasSize(1000);
    }

    @Test
    void deveRejeitarComentarioComMaisDeMilCaracteres() {
        var h = novo(); h.setComentario("a".repeat(1001));
        violacao(h, "22001", "comentario");
    }

    @Test
    void deveRejeitarVersaoNegativaNoHistorico() {
        var h = novo(); h.setVersaoDesignacao(-1);
        violacao(h, "23513", "ck_historico_designacao_versao");
    }

    @Test
    void deveRejeitarDuasDesignacoesComMesmaVersaoNoMesmoEvento() {
        var primeiro = repository.saveAndFlush(novo());
        var duplicado = historico(primeiro.getEventoOperacional(), primeiro.getUsuario(), primeiro.getEquipeAnterior(),
                primeiro.getEquipeNova(), primeiro.getVersaoDesignacao(), primeiro.getRegistradoEm());
        violacao(duplicado, "23505", "uq_designacao_evento_versao");
    }

    @Test
    void devePermitirMesmaVersaoEmEventosDistintos() {
        var primeiro = repository.saveAndFlush(novo());
        var outro = repository.saveAndFlush(novo());
        assertThat(primeiro.getEventoOperacional().getId()).isNotEqualTo(outro.getEventoOperacional().getId());
        assertThat(primeiro.getVersaoDesignacao()).isEqualTo(outro.getVersaoDesignacao());
    }

    @Test
    void deveConsultarPaginasPorInstanteDecrescenteComDesempatePorIdEIsolamentoPorEvento() {
        var primeiro = repository.saveAndFlush(novo());
        var evento = primeiro.getEventoOperacional();
        var segundo = repository.saveAndFlush(historico(evento, primeiro.getUsuario(), primeiro.getEquipeNova(),
                primeiro.getEquipeAnterior(), 2, primeiro.getRegistradoEm().plusHours(1)));
        var terceiro = repository.saveAndFlush(historico(evento, primeiro.getUsuario(), primeiro.getEquipeAnterior(),
                null, 3, segundo.getRegistradoEm()));
        repository.saveAndFlush(novo());
        Long eventoId = evento.getId(); em.clear();

        var pagina = repository.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(eventoId, PageRequest.of(0, 1));
        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(HistoricoDesignacaoEquipe::getId).containsExactly(terceiro.getId());
        assertThat(repository.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(eventoId, PageRequest.of(1, 1)).getContent())
                .extracting(HistoricoDesignacaoEquipe::getId).containsExactly(segundo.getId());
        assertThat(repository.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(eventoId, PageRequest.of(2, 1)).getContent())
                .extracting(HistoricoDesignacaoEquipe::getId).containsExactly(primeiro.getId());
        assertThat(repository.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(eventoId, PageRequest.of(3, 1))).isEmpty();
        assertThat(repository.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(Long.MAX_VALUE, PageRequest.of(0, 20))).isEmpty();
        assertThat(repository.countByEventoOperacionalId(eventoId)).isEqualTo(3);
        assertThat(repository.findByIdAndEventoOperacionalId(terceiro.getId(), eventoId)).isPresent();
        assertThat(repository.findByIdAndEventoOperacionalId(terceiro.getId(), Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void alteracoesJpaNaoDevemSobrescreverAuditoriaExistente() {
        var h = repository.saveAndFlush(novo());
        Long usuarioId = h.getUsuario().getId(); Long anteriorId = h.getEquipeAnterior().getId(); Long novaId = h.getEquipeNova().getId();
        var instante = h.getRegistradoEm().toInstant();
        h.setComentario("Tentativa de sobrescrita"); h.setVersaoDesignacao(99);
        h.setRegistradoEm(h.getRegistradoEm().plusDays(1)); h.setEquipeAnterior(null); h.setEquipeNova(null);
        h.setUsuario(h.getEventoOperacional().getResponsavelUsuario());
        em.flush(); em.clear();
        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getComentario()).isEqualTo("Equipe designada pelo operador.");
        assertThat(salvo.getVersaoDesignacao()).isEqualTo(1);
        assertThat(salvo.getRegistradoEm().toInstant()).isEqualTo(instante);
        assertThat(salvo.getUsuario().getId()).isEqualTo(usuarioId);
        assertThat(salvo.getEquipeAnterior().getId()).isEqualTo(anteriorId);
        assertThat(salvo.getEquipeNova().getId()).isEqualTo(novaId);
    }

    @Test
    void alteracaoDeEquipeAtualNaoDeveReescreverHistoricoAnterior() {
        var h = repository.saveAndFlush(novo());
        var evento = h.getEventoOperacional();
        evento.setEquipeDesignada(null); evento.setVersaoDesignacao(2);
        em.flush(); em.clear();
        var salvo = repository.findById(h.getId()).orElseThrow();
        assertThat(salvo.getEquipeNova().getId()).isEqualTo(h.getEquipeNova().getId());
        assertThat(salvo.getVersaoDesignacao()).isEqualTo(1);
        assertThat(salvo.getEventoOperacional().getEquipeDesignada()).isNull();
        assertThat(salvo.getEventoOperacional().getVersaoDesignacao()).isEqualTo(2);
    }

    @Test
    void eventoNovoDeveIniciarNaVersaoZeroSemEquipeENaoVersionarTransicaoDeSituacao() {
        var h = novo(); var evento = h.getEventoOperacional(); Long id = evento.getId();
        assertThat(evento.getVersaoDesignacao()).isZero();
        assertThat(evento.getEquipeDesignada()).isNull();
        evento.setSituacao("EM_ANDAMENTO");
        em.flush(); em.clear();
        var salvo = em.find(EventoOperacional.class, id);
        assertThat(salvo.getVersaoDesignacao()).isZero();
        assertThat(salvo.getSituacao()).isEqualTo("EM_ANDAMENTO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"versao_nula", "versao_negativa", "equipe_inexistente"})
    void bancoDeveProtegerVersaoEVinculoAtualDoEvento(String caso) {
        var evento = novo().getEventoOperacional();
        String sql = switch (caso) {
            case "versao_nula" -> "UPDATE evento_operacional SET versao_designacao = NULL WHERE id = ?";
            case "versao_negativa" -> "UPDATE evento_operacional SET versao_designacao = -1 WHERE id = ?";
            default -> "UPDATE evento_operacional SET equipe_designada_id = 9223372036854775807 WHERE id = ?";
        };
        var ex = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, evento.getId()));
        conferir(ex, caso.equals("versao_nula") ? "23502" : caso.equals("versao_negativa") ? "23513" : "23506",
                caso.equals("versao_nula") ? "versao_designacao" : caso.equals("versao_negativa") ? "ck_evento_versao_designacao" : "fk_evento_equipe");
    }

    private HistoricoDesignacaoEquipe novo() {
        var usuarios = em.createQuery("select u from Usuario u order by u.id", Usuario.class).setMaxResults(2).getResultList();
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var evento = new EventoOperacional(); evento.setAtivo(sub); evento.setCategoria(EventoOperacional.Categoria.MANUTENCAO);
        evento.setRegistradorUsuario(usuarios.getFirst()); evento.setResponsavelUsuario(usuarios.getFirst()); evento.setSituacao("ABERTO");
        em.persist(evento);
        return historico(evento, usuarios.getLast(), equipe("Anterior"), equipe("Nova"), 1,
                OffsetDateTime.parse("2026-11-03T10:00:00.123456-03:00"));
    }

    private EquipeManutencao equipe(String nome) {
        var equipe = new EquipeManutencao(); equipe.setNome(nome); equipe.setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        em.persist(equipe); return equipe;
    }

    private HistoricoDesignacaoEquipe historico(EventoOperacional evento, Usuario usuario, EquipeManutencao anterior,
            EquipeManutencao nova, int versao, OffsetDateTime instante) {
        var h = new HistoricoDesignacaoEquipe(); h.setEventoOperacional(evento); h.setUsuario(usuario);
        h.setEquipeAnterior(anterior); h.setEquipeNova(nova); h.setVersaoDesignacao(versao);
        h.setRegistradoEm(instante); h.setComentario("Equipe designada pelo operador."); return h;
    }

    private void violacao(HistoricoDesignacaoEquipe h, String state, String mensagem) {
        conferir(assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(h)), state, mensagem);
    }

    private void conferir(DataIntegrityViolationException ex, String state, String mensagem) {
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(state);
            assertThat(cause.getMessage()).containsIgnoringCase(mensagem);
        });
    }
}
