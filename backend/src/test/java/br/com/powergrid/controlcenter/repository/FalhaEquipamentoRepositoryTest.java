package br.com.powergrid.controlcenter.repository;

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
class FalhaEquipamentoRepositoryTest {
    private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-11-04T10:15:30.123456-03:00");
    @Autowired private FalhaEquipamentoRepository repository;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void devePersistirEventoEFalhaComMesmaChaveERecuperarRelacionamentoLazy() {
        var falha = nova();
        Long eventoId = falha.getEventoOperacional().getId();
        assertThat(falha.getId()).isNull();
        var salva = repository.saveAndFlush(falha);
        assertThat(salva.getId()).isEqualTo(eventoId);
        em.clear();
        var recuperada = repository.findById(eventoId).orElseThrow();
        assertThat(recuperada.getId()).isEqualTo(eventoId);
        assertThat(recuperada.getEstadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(recuperada.getEstadoAposReparo()).isNull();
        assertThat(recuperada.getRegistradoEm().toInstant()).isEqualTo(INSTANTE.toInstant());
        assertThat(Hibernate.isInitialized(recuperada.getEventoOperacional())).isFalse();
        assertThat(recuperada.getEventoOperacional().getId()).isEqualTo(eventoId);
        assertThat(recuperada.getEventoOperacional().getCategoria()).isEqualTo(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        assertThat(recuperada.getEventoOperacional().getSituacao()).isEqualTo("ABERTO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM falha_equipamento WHERE evento_operacional_id = ?", Long.class, eventoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_operacional WHERE id = ?", Long.class, eventoId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void deveAceitarEstadoAnteriorInclusiveFalhaLegada(EstadoOperacional estado) {
        var falha = nova(); falha.setEstadoAnterior(estado);
        var salva = repository.saveAndFlush(falha); Long id = salva.getId(); em.clear();
        assertThat(repository.findById(id).orElseThrow().getEstadoAnterior()).isEqualTo(estado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERACIONAL", "MANUTENCAO"})
    void devePermitirPersistirEstadoAposReparoSemAlterarSnapshots(String estado) {
        var falha = repository.saveAndFlush(nova()); Long id = falha.getId(); em.clear();
        var salva = repository.findById(id).orElseThrow();
        salva.setEstadoAposReparo(EstadoOperacional.valueOf(estado));
        repository.saveAndFlush(salva); em.clear();
        var recuperada = repository.findById(id).orElseThrow();
        assertThat(recuperada.getEstadoAposReparo()).isEqualTo(EstadoOperacional.valueOf(estado));
        assertThat(recuperada.getEstadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(recuperada.getRegistradoEm().toInstant()).isEqualTo(INSTANTE.toInstant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado_anterior", "registrado_em"})
    void deveRejeitarCamposObrigatoriosNulosNoBanco(String campo) {
        var falha = nova();
        if (campo.equals("estado_anterior")) falha.setEstadoAnterior(null);
        else falha.setRegistradoEm(null);
        var ex = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(falha));
        conferir(ex, "23502", campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OUTRO", "operacional"})
    void deveRejeitarEstadoAnteriorForaDoDominio(String estado) {
        var salva = repository.saveAndFlush(nova());
        var ex = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE falha_equipamento SET estado_anterior = ? WHERE evento_operacional_id = ?", estado, salva.getId()));
        conferir(ex, "23513", "ck_falha_equipamento_estado_anterior");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FALHA", "OUTRO"})
    void deveRejeitarEstadoAposReparoInvalidoNoBanco(String estado) {
        var salva = repository.saveAndFlush(nova());
        var ex = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE falha_equipamento SET estado_apos_reparo = ? WHERE evento_operacional_id = ?", estado, salva.getId()));
        conferir(ex, "23513", "ck_falha_equipamento_estado_apos_reparo");
    }

    @Test
    void deveRejeitarFalhaSemEventoNoBanco() {
        var ex = assertThrows(DataIntegrityViolationException.class, () -> inserir(null));
        conferir(ex, "23502", "evento_operacional_id");
    }

    @Test
    void deveRejeitarEventoInexistentePelaForeignKey() {
        var ex = assertThrows(DataIntegrityViolationException.class, () -> inserir(Long.MAX_VALUE));
        conferir(ex, "23506", "fk_falha_equipamento_evento");
    }

    @Test
    void deveImpedirDuasFalhasComMesmoEventoPelaChavePrimaria() {
        var salva = repository.saveAndFlush(nova());
        var ex = assertThrows(DataIntegrityViolationException.class, () -> inserir(salva.getId()));
        conferir(ex, "23505", "primary key");
    }

    @Test
    void deveImpedirExclusaoDeEventoReferenciadoSemApagarComposicao() {
        var salva = repository.saveAndFlush(nova());
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM evento_operacional WHERE id = ?", salva.getId()));
        conferir(ex, "23503", "fk_falha_equipamento_evento");
    }

    @Test
    void excluirComposicaoNaoDeveExcluirEventoOuAtivoEmCascata() {
        var salva = repository.saveAndFlush(nova()); Long id = salva.getId();
        Long ativoId = salva.getEventoOperacional().getAtivo().getId();
        repository.deleteById(id); repository.flush(); em.clear();
        assertThat(repository.findById(id)).isEmpty();
        assertThat(em.find(EventoOperacional.class, id)).isNotNull();
        assertThat(em.find(AtivoRede.class, ativoId)).isNotNull();
    }

    @Test
    void devePreservarSnapshotsOriginaisAoAtualizarEntidade() {
        var salva = repository.saveAndFlush(nova()); Long id = salva.getId();
        salva.setEstadoAnterior(EstadoOperacional.MANUTENCAO);
        salva.setRegistradoEm(INSTANTE.plusDays(1));
        repository.flush(); em.clear();
        var recuperada = repository.findById(id).orElseThrow();
        assertThat(recuperada.getEstadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(recuperada.getRegistradoEm().toInstant()).isEqualTo(INSTANTE.toInstant());
    }

    @Test
    void deveConsultarComposicoesIndependentesPelaIdentidadeDoEvento() {
        var primeira = nova(); var segunda = nova();
        Long primeiroId = primeira.getEventoOperacional().getId();
        Long segundoId = segunda.getEventoOperacional().getId();
        assertThat(repository.findById(primeiroId)).isEmpty();
        repository.saveAndFlush(primeira); repository.saveAndFlush(segunda); em.clear();
        assertThat(repository.findAllById(java.util.List.of(primeiroId, segundoId)))
                .extracting(FalhaEquipamento::getId).containsExactlyInAnyOrder(primeiroId, segundoId);
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    private FalhaEquipamento nova() {
        var usuario = em.createQuery("select u from Usuario u order by u.id", Usuario.class).setMaxResults(1).getSingleResult();
        var ativo = new Subestacao(); ativo.setNome("Equipamento do teste de falha");
        ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(ativo);
        var evento = new EventoOperacional(); evento.setAtivo(ativo);
        evento.setCategoria(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        evento.setSituacao("ABERTO"); evento.setDescricao("Defeito identificado na inspeção");
        evento.setRegistradorUsuario(usuario); evento.setResponsavelUsuario(usuario);
        em.persist(evento); em.flush();
        var falha = new FalhaEquipamento(); falha.setEventoOperacional(evento);
        falha.setEstadoAnterior(EstadoOperacional.OPERACIONAL); falha.setRegistradoEm(INSTANTE);
        return falha;
    }

    private void inserir(Long eventoId) {
        jdbc.update("INSERT INTO falha_equipamento (evento_operacional_id, estado_anterior, registrado_em) VALUES (?, 'OPERACIONAL', ?)", eventoId, INSTANTE);
    }

    private void conferir(DataIntegrityViolationException ex, String sqlState, String trecho) {
        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, causa -> {
            assertThat(causa.getSQLState()).isEqualTo(sqlState);
            assertThat(causa.getMessage()).containsIgnoringCase(trecho);
        });
    }
}
