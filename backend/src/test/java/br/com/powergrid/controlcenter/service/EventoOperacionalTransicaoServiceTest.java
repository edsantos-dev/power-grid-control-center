package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static br.com.powergrid.controlcenter.model.SituacaoEventoOperacional.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class EventoOperacionalTransicaoServiceTest {
    @Autowired private EventoOperacionalService service;
    @Autowired private HistoricoEventoOperacionalRepository historicos;
    @Autowired private EntityManager em;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00.123456789Z");

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @AfterEach
    void limparAutenticacao() { SecurityContextHolder.clearContext(); }

    @Test
    void deveExecutarDuasTransicoesComDatasEAuditoriaSemAlterarResponsabilidadesOuPrevisoes() {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        Long id = evento.getId(); Long registrador = evento.getRegistradorUsuario().getId(); Long responsavel = evento.getResponsavelUsuario().getId();
        var previsto = evento.getInicioPrevisto(); var terminoPrevisto = evento.getTerminoPrevisto();
        var primeiro = service.transicionar(id, ABERTO, EM_ANDAMENTO, "Atendimento iniciado");
        em.clear();
        var iniciado = em.find(EventoOperacional.class, id);
        assertThat(iniciado.getSituacao()).isEqualTo("EM_ANDAMENTO");
        assertThat(iniciado.getInicioEfetivo().toInstant()).isEqualTo(AGORA.truncatedTo(ChronoUnit.MICROS));
        assertThat(iniciado.getTerminoEfetivo()).isNull();
        when(clock.instant()).thenReturn(AGORA.plusSeconds(600));
        var segundo = service.transicionar(id, EM_ANDAMENTO, CONCLUIDO, "Atendimento concluído");
        em.clear();
        var concluido = em.find(EventoOperacional.class, id);
        assertThat(concluido.getSituacao()).isEqualTo("CONCLUIDO");
        assertThat(concluido.getInicioEfetivo().toInstant()).isEqualTo(AGORA.truncatedTo(ChronoUnit.MICROS));
        assertThat(concluido.getTerminoEfetivo().toInstant()).isEqualTo(AGORA.plusSeconds(600).truncatedTo(ChronoUnit.MICROS));
        assertThat(concluido.getInicioPrevisto()).isEqualTo(previsto); assertThat(concluido.getTerminoPrevisto()).isEqualTo(terminoPrevisto);
        assertThat(concluido.getRegistradorUsuario().getId()).isEqualTo(registrador);
        assertThat(concluido.getResponsavelUsuario().getId()).isEqualTo(responsavel);
        var h1 = historicos.findById(primeiro).orElseThrow(); var h2 = historicos.findById(segundo).orElseThrow();
        assertThat(h1.getSituacaoAnterior()).isEqualTo(ABERTO); assertThat(h1.getSituacaoNova()).isEqualTo(EM_ANDAMENTO);
        assertThat(h2.getSituacaoAnterior()).isEqualTo(EM_ANDAMENTO); assertThat(h2.getSituacaoNova()).isEqualTo(CONCLUIDO);
        assertThat(h1.getComentario()).isEqualTo("Atendimento iniciado"); assertThat(h2.getComentario()).isEqualTo("Atendimento concluído");
        assertThat(h1.getUsuario().getId()).isEqualTo(responsavel); assertThat(h2.getUsuario().getId()).isEqualTo(responsavel);
        assertThat(h1.getAlteradoEm().toInstant()).isEqualTo(concluido.getInicioEfetivo().toInstant());
        assertThat(h2.getAlteradoEm().toInstant()).isEqualTo(concluido.getTerminoEfetivo().toInstant());
        assertThat(h2.getAlteradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(historicos.findByEventoId(id, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void deveAutorizarResponsaveisEGerenteParaEventoDeOutroUsuario(PerfilAcesso perfil) {
        var responsavel = perfil == PerfilAcesso.GERENTE_OPERACOES ? PerfilAcesso.OPERADOR : perfil;
        var evento = EventosTransicaoFixture.evento(em, responsavel); autenticar(perfil);
        Long historico = service.transicionar(evento.getId(), ABERTO, EM_ANDAMENTO, "a".repeat(1000));
        assertThat(historicos.findById(historico).orElseThrow().getUsuario().getId())
                .isEqualTo(EventosTransicaoFixture.usuario(em, perfil).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveNegarOutroResponsavelMesmoComAuthorityDeGerente(PerfilAcesso perfil) {
        var responsavel = perfil == PerfilAcesso.OPERADOR ? PerfilAcesso.TECNICO_MANUTENCAO : PerfilAcesso.OPERADOR;
        var evento = EventosTransicaoFixture.evento(em, responsavel); autenticar(perfil);
        assertThrows(AccessDeniedException.class, () -> service.transicionar(evento.getId(), ABERTO, EM_ANDAMENTO, "Atendimento"));
        inalterado(evento.getId(), "ABERTO");
    }

    @Test
    void deveRejeitarSituacaoEsperadaDivergente() {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        erro(HttpStatus.CONFLICT, () -> service.transicionar(evento.getId(), EM_ANDAMENTO, CONCLUIDO, "Conclusão"));
        inalterado(evento.getId(), "ABERTO");
    }

    @ParameterizedTest
    @CsvSource({"ABERTO,ABERTO", "ABERTO,CONCLUIDO", "EM_ANDAMENTO,ABERTO", "EM_ANDAMENTO,EM_ANDAMENTO",
            "CONCLUIDO,ABERTO", "CONCLUIDO,EM_ANDAMENTO", "CONCLUIDO,CONCLUIDO"})
    void deveRejeitarSaltosRetornosERepeticoes(SituacaoEventoOperacional anterior, SituacaoEventoOperacional nova) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR); evento.setSituacao(anterior.name()); em.flush();
        erro(HttpStatus.CONFLICT, () -> service.transicionar(evento.getId(), anterior, nova, "Alteração"));
        inalterado(evento.getId(), anterior.name());
    }

    @Test
    void deveManterInicioEfetivoPreexistente() {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        var inicio = AGORA.minusSeconds(100).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.ofHours(-3));
        evento.setInicioEfetivo(inicio); em.flush();
        service.transicionar(evento.getId(), ABERTO, EM_ANDAMENTO, "Atendimento"); em.clear();
        assertThat(em.find(EventoOperacional.class, evento.getId()).getInicioEfetivo().toInstant()).isEqualTo(inicio.toInstant());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarConclusaoSemInicioOuComInicioPosteriorAoRelogio(boolean futuro) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR); evento.setSituacao("EM_ANDAMENTO");
        if (futuro) evento.setInicioEfetivo(AGORA.plusSeconds(1).atOffset(ZoneOffset.UTC));
        em.flush();
        erro(HttpStatus.CONFLICT, () -> service.transicionar(evento.getId(), EM_ANDAMENTO, CONCLUIDO, "Conclusão"));
        inalterado(evento.getId(), "EM_ANDAMENTO");
        assertThat(em.find(EventoOperacional.class, evento.getId()).getTerminoEfetivo()).isNull();
    }

    @Test
    void deveAceitarTerminoIgualAoInicio() {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR); evento.setSituacao("EM_ANDAMENTO");
        var inicio = AGORA.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC); evento.setInicioEfetivo(inicio); em.flush();
        service.transicionar(evento.getId(), EM_ANDAMENTO, CONCLUIDO, "Conclusão"); em.clear();
        var salvo = em.find(EventoOperacional.class, evento.getId());
        assertThat(salvo.getTerminoEfetivo().toInstant()).isEqualTo(salvo.getInicioEfetivo().toInstant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABERTO", "EM_ANDAMENTO"})
    void devePreservarTerminoJaExistenteERejeitarLegadoInconsistente(SituacaoEventoOperacional atual) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR); evento.setSituacao(atual.name());
        var inicio = AGORA.minusSeconds(60).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        evento.setInicioEfetivo(inicio); evento.setTerminoEfetivo(inicio.plusSeconds(10)); em.flush();
        erro(HttpStatus.CONFLICT, () -> service.transicionar(evento.getId(), atual, atual == ABERTO ? EM_ANDAMENTO : CONCLUIDO, "Alteração"));
        inalterado(evento.getId(), atual.name());
        assertThat(em.find(EventoOperacional.class, evento.getId()).getTerminoEfetivo()).isEqualTo(inicio.plusSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nulo", "vazio", "espacos", "longo", "esperada", "destino"})
    void deveRejeitarContratoInvalido(String caso) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        String comentario = switch (caso) { case "nulo" -> null; case "vazio" -> ""; case "espacos" -> " 	 "; case "longo" -> "a".repeat(1001); default -> "Justificativa"; };
        erro(HttpStatus.BAD_REQUEST, () -> service.transicionar(evento.getId(), caso.equals("esperada") ? null : ABERTO,
                caso.equals("destino") ? null : EM_ANDAMENTO, comentario));
        inalterado(evento.getId(), "ABERTO");
    }

    @Test
    void deveRejeitarEventoInexistente() {
        erro(HttpStatus.NOT_FOUND, () -> service.transicionar(Long.MAX_VALUE, ABERTO, EM_ANDAMENTO, "Atendimento"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"semSessao", "anonimo", "usuarioAusente"})
    void deveExigirUsuarioAutenticadoExistente(String caso) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        SecurityContextHolder.clearContext();
        if (caso.equals("anonimo")) SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("anonimo", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        if (caso.equals("usuarioAusente")) EventosTransicaoFixture.autenticar("ausente@teste.local");
        erro(HttpStatus.UNAUTHORIZED, () -> service.transicionar(evento.getId(), ABERTO, EM_ANDAMENTO, "Atendimento"));
        inalterado(evento.getId(), "ABERTO");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarSituacaoLegadaDesconhecidaOuAusente(boolean ausente) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        String atual = ausente ? null : "IMPORTADO"; evento.setSituacao(atual); em.flush();
        erro(HttpStatus.CONFLICT, () -> service.transicionar(evento.getId(), ABERTO, EM_ANDAMENTO, "Atendimento"));
        inalterado(evento.getId(), atual);
    }

    private void autenticar(PerfilAcesso perfil) {
        EventosTransicaoFixture.autenticar("  " + EventosTransicaoFixture.usuario(em, perfil).getEmail().toUpperCase(java.util.Locale.ROOT) + "  ");
    }

    private void inalterado(Long id, String situacao) {
        em.clear(); assertThat(em.find(EventoOperacional.class, id).getSituacao()).isEqualTo(situacao);
        assertThat(historicos.findByEventoId(id, PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    private void erro(HttpStatus status, org.junit.jupiter.api.function.Executable acao) {
        assertThat(assertThrows(ResponseStatusException.class, acao).getStatusCode()).isEqualTo(status);
    }
}
