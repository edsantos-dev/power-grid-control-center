package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
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
class EventoOperacionalRepositoryTest {

    @Autowired private EventoOperacionalRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR"})
    void devePersistirERecuperarRelacionamentosPolimorficos(String tipo) {
        EventoOperacional evento = novoEvento(tipo);
        Usuario responsavel = salvarUsuario("responsavel");
        evento.setResponsavelUsuario(responsavel);
        evento.setCriticidade("ALTA");
        evento.setTerminoPrevisto(evento.getInicioPrevisto().plusHours(1));
        Long ativoId = evento.getAtivo().getId();
        Long registradorId = evento.getRegistradorUsuario().getId();
        Class<?> tipoAtivo = evento.getAtivo().getClass();
        repository.saveAndFlush(evento);
        entityManager.clear();

        EventoOperacional salvo = repository.findById(evento.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getCategoria()).isEqualTo(Categoria.MANOBRA);
        assertThat(salvo.getDescricao()).isEqualTo(evento.getDescricao());
        assertThat(salvo.getSituacao()).isEqualTo("ABERTO");
        assertThat(salvo.getCriticidade()).isEqualTo("ALTA");
        assertThat(salvo.getInicioPrevisto().toInstant()).isEqualTo(evento.getInicioPrevisto().toInstant());
        assertThat(salvo.getTerminoPrevisto().toInstant()).isEqualTo(evento.getTerminoPrevisto().toInstant());
        assertThat(salvo.getInicioEfetivo()).isNull();
        assertThat(salvo.getTerminoEfetivo()).isNull();
        assertThat(Hibernate.isInitialized(salvo.getRegistradorUsuario())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getResponsavelUsuario())).isFalse();
        assertThat(salvo.getAtivo().getId()).isEqualTo(ativoId);
        assertThat(Hibernate.unproxy(salvo.getAtivo())).isInstanceOf(tipoAtivo);
        assertThat(salvo.getRegistradorUsuario().getId()).isEqualTo(registradorId);
        assertThat(salvo.getResponsavelUsuario().getId()).isEqualTo(responsavel.getId());
        assertThat(salvo.getRegistradorUsuario().getEmail()).isEqualTo("registrador@teste.local");
        assertThat(salvo.getResponsavelUsuario().getEmail()).isEqualTo("responsavel@teste.local");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT equipe_designada_id FROM evento_operacional WHERE id = ?", Long.class, salvo.getId())).isNull();
    }

    @ParameterizedTest
    @EnumSource(Categoria.class)
    void deveAceitarCategoriasDaV1EMesmoUsuarioNosDoisPapeis(Categoria categoria) {
        EventoOperacional evento = novoEvento("SUBESTACAO");
        evento.setCategoria(categoria);
        repository.saveAndFlush(evento);
        entityManager.clear();
        EventoOperacional salvo = repository.findById(evento.getId()).orElseThrow();
        assertThat(salvo.getCategoria()).isEqualTo(categoria);
        assertThat(salvo.getRegistradorUsuario().getId()).isEqualTo(salvo.getResponsavelUsuario().getId());
        assertThat(salvo.getTerminoPrevisto()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo", "registrador", "responsavel", "categoria"})
    void deveRejeitarCampoObrigatorioNulo(String campo) {
        EventoOperacional evento = novoEvento("SUBESTACAO");
        switch (campo) {
            case "ativo" -> evento.setAtivo(null);
            case "registrador" -> evento.setRegistradorUsuario(null);
            case "responsavel" -> evento.setResponsavelUsuario(null);
            case "categoria" -> evento.setCategoria(null);
        }
        assertViolacao(evento, "23502");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativo", "registrador", "responsavel"})
    void deveRejeitarReferenciaInexistente(String campo) {
        EventoOperacional evento = novoEvento("SUBESTACAO");
        switch (campo) {
            case "ativo" -> evento.setAtivo(entityManager.getReference(Subestacao.class, Long.MAX_VALUE));
            case "registrador" -> evento.setRegistradorUsuario(entityManager.getReference(Usuario.class, Long.MAX_VALUE));
            case "responsavel" -> evento.setResponsavelUsuario(entityManager.getReference(Usuario.class, Long.MAX_VALUE));
        }
        assertViolacao(evento, "23506");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarTerminoAnteriorAoInicio(boolean efetivo) {
        EventoOperacional evento = novoEvento("SUBESTACAO");
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-25T10:00:00-03:00");
        OffsetDateTime termino = OffsetDateTime.parse("2026-10-25T12:59:59Z");
        if (efetivo) {
            evento.setInicioEfetivo(inicio);
            evento.setTerminoEfetivo(termino);
        } else {
            evento.setInicioPrevisto(inicio);
            evento.setTerminoPrevisto(termino);
        }
        assertViolacao(evento, "23513");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deveAceitarTerminosIguaisOuPosterioresConsiderandoFuso(int horas) {
        EventoOperacional evento = novoEvento("SUBESTACAO");
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-25T10:00:00-03:00");
        OffsetDateTime termino = OffsetDateTime.parse("2026-10-25T13:00:00Z").plusHours(horas);
        evento.setInicioPrevisto(inicio);
        evento.setTerminoPrevisto(termino);
        evento.setInicioEfetivo(inicio);
        evento.setTerminoEfetivo(termino);
        repository.saveAndFlush(evento);
        entityManager.clear();
        EventoOperacional salvo = repository.findById(evento.getId()).orElseThrow();
        assertThat(salvo.getTerminoPrevisto().toInstant()).isEqualTo(termino.toInstant());
        assertThat(salvo.getInicioEfetivo().toInstant()).isEqualTo(inicio.toInstant());
        assertThat(salvo.getTerminoEfetivo().toInstant()).isEqualTo(termino.toInstant());
    }

    private EventoOperacional novoEvento(String tipo) {
        Usuario usuario = salvarUsuario("registrador");
        Subestacao subestacao = new Subestacao();
        preencherAtivo(subestacao);
        subestacao.setCapacidadeMva(new BigDecimal("100"));
        subestacao.setTensaoNominalKv(new BigDecimal("138"));
        entityManager.persist(subestacao);
        AtivoRede ativo = subestacao;
        if (!tipo.equals("SUBESTACAO")) {
            Alimentador alimentador = new Alimentador();
            preencherAtivo(alimentador);
            alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
            alimentador.setSubestacao(subestacao);
            entityManager.persist(alimentador);
            ativo = alimentador;
            if (tipo.equals("TRANSFORMADOR")) {
                Transformador transformador = new Transformador();
                preencherAtivo(transformador);
                transformador.setCapacidadeMva(new BigDecimal("5"));
                transformador.setAlimentador(alimentador);
                entityManager.persist(transformador);
                ativo = transformador;
            }
        }
        entityManager.flush();
        EventoOperacional evento = new EventoOperacional();
        evento.setAtivo(ativo);
        evento.setCategoria(Categoria.MANOBRA);
        evento.setDescricao("Manobra planejada para inspeção.");
        evento.setSituacao("ABERTO");
        evento.setRegistradorUsuario(usuario);
        evento.setResponsavelUsuario(usuario);
        evento.setInicioPrevisto(OffsetDateTime.parse("2026-10-25T10:00:00-03:00"));
        return evento;
    }

    private Usuario salvarUsuario(String identificador) {
        Usuario usuario = new Usuario();
        usuario.setNome(identificador);
        usuario.setEmail(identificador + "@teste.local");
        usuario.setMatricula(identificador);
        usuario.setPerfil(PerfilAcesso.OPERADOR);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private void preencherAtivo(AtivoRede ativo) {
        ativo.setNome("Ativo de teste");
        ativo.setLatitude(new BigDecimal("-25.4284"));
        ativo.setLongitude(new BigDecimal("-49.2733"));
        ativo.setDataInstalacao(LocalDate.of(2026, 1, 1));
        ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
    }

    private void assertViolacao(EventoOperacional evento, String sqlState) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(evento));
        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
    }
}
