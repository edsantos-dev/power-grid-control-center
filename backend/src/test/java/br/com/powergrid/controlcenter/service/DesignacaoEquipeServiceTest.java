package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.*;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture.Dados;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class DesignacaoEquipeServiceTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00.123456789Z");
    @Autowired private DesignacaoEquipeService service;
    @Autowired private EntityManager em;
    @Autowired private HistoricoDesignacaoEquipeRepository historicos;
    @MockitoBean private Clock clock;
    private Dados dados;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = DesignacaoEquipeFixture.criar(em);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @Test
    void deveDesignarSubstituirERetirarComVersoesEAuditoriaPreservadas() {
        var original = evento();
        Long responsavelId = original.getResponsavelUsuario().getId();
        Long registradorId = original.getRegistradorUsuario().getId();
        var previsto = original.getInicioPrevisto();
        var primeira = service.designar(dados.eventoId(), dados.equipeAId(), 0, "Designação inicial", dados.operadorId());
        assertThat(primeira.getVersaoDesignacao()).isEqualTo(1);
        assertThat(primeira.getEquipeDesignada().getId()).isEqualTo(dados.equipeAId());
        var segunda = service.designar(dados.eventoId(), dados.equipeBId(), 1, "Substituição", dados.operadorId());
        assertThat(segunda.getVersaoDesignacao()).isEqualTo(2);
        assertThat(segunda.getEquipeDesignada().getId()).isEqualTo(dados.equipeBId());
        var retirada = service.designar(dados.eventoId(), null, 2, "Retirada", dados.operadorId());
        assertThat(retirada.getVersaoDesignacao()).isEqualTo(3); assertThat(retirada.getEquipeDesignada()).isNull();
        var registros = em.createQuery("select h from HistoricoDesignacaoEquipe h where h.eventoOperacional.id = :id order by h.versaoDesignacao",
                HistoricoDesignacaoEquipe.class).setParameter("id", dados.eventoId()).getResultList();
        assertThat(registros).hasSize(3);
        assertThat(registros).extracting(HistoricoDesignacaoEquipe::getVersaoDesignacao).containsExactly(1, 2, 3);
        assertThat(registros).extracting(HistoricoDesignacaoEquipe::getComentario).containsExactly("Designação inicial", "Substituição", "Retirada");
        assertThat(registros.get(0).getEquipeAnterior()).isNull();
        assertThat(registros.get(0).getEquipeNova().getId()).isEqualTo(dados.equipeAId());
        assertThat(registros.get(1).getEquipeAnterior().getId()).isEqualTo(dados.equipeAId());
        assertThat(registros.get(1).getEquipeNova().getId()).isEqualTo(dados.equipeBId());
        assertThat(registros.get(2).getEquipeAnterior().getId()).isEqualTo(dados.equipeBId());
        assertThat(registros.get(2).getEquipeNova()).isNull();
        assertThat(registros).allSatisfy(h -> {
            assertThat(h.getUsuario().getId()).isEqualTo(dados.operadorId());
            assertThat(h.getRegistradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            assertThat(h.getRegistradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        });
        assertThat(retirada.getSituacao()).isEqualTo("ABERTO");
        assertThat(retirada.getInicioEfetivo()).isNull(); assertThat(retirada.getTerminoEfetivo()).isNull();
        assertThat(retirada.getInicioPrevisto()).isEqualTo(previsto);
        assertThat(retirada.getRegistradorUsuario().getId()).isEqualTo(registradorId);
        assertThat(retirada.getResponsavelUsuario().getId()).isEqualTo(responsavelId);
        assertThat(em.find(EquipeManutencao.class, dados.equipeAId()).getSituacao()).isEqualTo(SituacaoEquipe.DISPONIVEL);
        assertThat(em.find(EquipeManutencao.class, dados.equipeBId()).getSituacao()).isEqualTo(SituacaoEquipe.EM_ATENDIMENTO);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirGerenteOuOperadorTecnicoResponsavel(PerfilAcesso perfil) {
        var usuario = EventosTransicaoFixture.usuario(em, perfil);
        if (perfil != PerfilAcesso.GERENTE_OPERACOES) evento().setResponsavelUsuario(usuario);
        autenticar(perfil);
        var salvo = service.designar(dados.eventoId(), dados.equipeAId(), 0, "Atendimento autorizado", usuario.getId());
        assertThat(salvo.getVersaoDesignacao()).isEqualTo(1);
        assertThat(historicos.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(dados.eventoId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst().getUsuario().getId()).isEqualTo(usuario.getId());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveNegarNaoResponsavelMesmoComAuthorityDeGerenteNoPrincipal(PerfilAcesso perfil) {
        evento().setResponsavelUsuario(EventosTransicaoFixture.usuario(em, PerfilAcesso.GERENTE_OPERACOES));
        autenticar(perfil);
        Long id = EventosTransicaoFixture.usuario(em, perfil).getId();
        assertThrows(AccessDeniedException.class, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Não autorizado", id));
        semAlteracao();
    }

    @Test
    void deveRejeitarIdentidadeDiferenteDaSessao() {
        assertThrows(AccessDeniedException.class, () ->
                service.designar(dados.eventoId(), dados.equipeAId(), 0, "Identidade divergente", dados.gerenteId()));
        semAlteracao();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUSENTE", "ANONIMO", "NAO_AUTENTICADO", "USUARIO_INEXISTENTE"})
    void deveExigirSessaoValida(String caso) {
        SecurityContextHolder.clearContext();
        if (caso.equals("ANONIMO")) SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("teste", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        if (caso.equals("NAO_AUTENTICADO")) SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("operador", ""));
        if (caso.equals("USUARIO_INEXISTENTE")) EventosTransicaoFixture.autenticar("inexistente@powergrid.local");
        status(401, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Sem sessão válida", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveSerIdempotenteSemEquipeESemHistoricoInicial() {
        var resultado = service.designar(dados.eventoId(), null, 0, "Nada a retirar", dados.operadorId());
        assertThat(resultado.getVersaoDesignacao()).isZero();
        semAlteracao();
    }

    @Test
    void deveSerIdempotenteSemAlterarVersaoAutorInstanteOuComentarioOriginais() {
        service.designar(dados.eventoId(), dados.equipeAId(), 0, "Primeiro autor", dados.operadorId());
        var primeiro = historicos.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(dados.eventoId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst();
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
        var repetido = service.designar(dados.eventoId(), dados.equipeAId(), 1, "Outro comentário", dados.gerenteId());
        assertThat(repetido.getVersaoDesignacao()).isEqualTo(1);
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isEqualTo(1);
        assertThat(primeiro.getUsuario().getId()).isEqualTo(dados.operadorId());
        assertThat(primeiro.getComentario()).isEqualTo("Primeiro autor");
        assertThat(primeiro.getRegistradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void versaoAntigaDeveConflitarMesmoQuandoEquipeSolicitadaJaForAtual() {
        service.designar(dados.eventoId(), dados.equipeAId(), 0, "Primeira designação", dados.operadorId());
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Repetição desatualizada", dados.operadorId()));
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isEqualTo(1);
        assertThat(evento().getVersaoDesignacao()).isEqualTo(1);
    }

    @Test
    void deveDetectarVersaoAntigaAposEquipeVoltarAoMesmoId() {
        service.designar(dados.eventoId(), dados.equipeAId(), 0, "A", dados.operadorId());
        service.designar(dados.eventoId(), dados.equipeBId(), 1, "B", dados.operadorId());
        service.designar(dados.eventoId(), dados.equipeAId(), 2, "A novamente", dados.operadorId());
        status(409, () -> service.designar(dados.eventoId(), dados.equipeBId(), 1, "Estado antigo", dados.operadorId()));
        assertThat(evento().getVersaoDesignacao()).isEqualTo(3);
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONCLUIDO", "IMPORTADO", ""})
    void deveBloquearEventosForaDosEstadosPermitidos(String situacao) {
        evento().setSituacao(situacao);
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Estado inválido", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveBloquearSituacaoNulaInclusiveRetiradaIdempotente() {
        evento().setSituacao(null);
        status(409, () -> service.designar(dados.eventoId(), null, 0, "Retirada", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void devePermitirDesignacaoEmEventoEmAndamento() {
        evento().setSituacao("EM_ANDAMENTO");
        assertThat(service.designar(dados.eventoId(), dados.equipeAId(), 0, "Atendimento em curso", dados.operadorId())
                .getSituacao()).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    void deveBloquearEquipeForaDeServico() {
        em.find(EquipeManutencao.class, dados.equipeAId()).setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Equipe indisponível", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveBloquearEquipeComSituacaoLegadaNula() {
        em.find(EquipeManutencao.class, dados.equipeAId()).setSituacao(null);
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Equipe sem situação", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveBloquearEquipeSemIntegrantesAtivos() {
        em.createQuery("update VinculoEquipe v set v.fimVigencia = :fim where v.equipe.id = :id")
                .setParameter("fim", AGORA.atOffset(ZoneOffset.UTC)).setParameter("id", dados.equipeAId()).executeUpdate();
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), 0, "Equipe sem integrantes", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void devePermitirRetirarEquipeQuePosteriormenteFicouForaDeServico() {
        service.designar(dados.eventoId(), dados.equipeAId(), 0, "Designação", dados.operadorId());
        em.find(EquipeManutencao.class, dados.equipeAId()).setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        var retirado = service.designar(dados.eventoId(), null, 1, "Retirada", dados.operadorId());
        assertThat(retirado.getEquipeDesignada()).isNull(); assertThat(retirado.getVersaoDesignacao()).isEqualTo(2);
    }

    @Test
    void deveRetornar404ParaEventoOuEquipeInexistentes() {
        status(404, () -> service.designar(Long.MAX_VALUE, dados.equipeAId(), 0, "Evento ausente", dados.operadorId()));
        status(404, () -> service.designar(dados.eventoId(), Long.MAX_VALUE, 0, "Equipe ausente", dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveAceitarComentarioNoLimite() {
        service.designar(dados.eventoId(), dados.equipeAId(), 0, "a".repeat(1000), dados.operadorId());
        assertThat(historicos.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(dados.eventoId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst().getComentario()).hasSize(1000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENTO_NULO", "EVENTO_ZERO", "EQUIPE_ZERO", "VERSAO_NULA", "VERSAO_NEGATIVA",
            "COMENTARIO_NULO", "COMENTARIO_VAZIO", "COMENTARIO_BRANCO", "COMENTARIO_LONGO"})
    void deveRejeitarContratoInvalido(String caso) {
        Long eventoId = caso.equals("EVENTO_NULO") ? null : caso.equals("EVENTO_ZERO") ? 0L : dados.eventoId();
        Long equipeId = caso.equals("EQUIPE_ZERO") ? 0L : dados.equipeAId();
        Integer versao = caso.equals("VERSAO_NULA") ? null : caso.equals("VERSAO_NEGATIVA") ? -1 : 0;
        String comentario = switch (caso) {
            case "COMENTARIO_NULO" -> null;
            case "COMENTARIO_VAZIO" -> "";
            case "COMENTARIO_BRANCO" -> "  ";
            case "COMENTARIO_LONGO" -> "a".repeat(1001);
            default -> "Designação";
        };
        status(400, () -> service.designar(eventoId, equipeId, versao, comentario, dados.operadorId()));
        semAlteracao();
    }

    @Test
    void deveImpedirEstouroDaVersaoSemAlterarVinculoOuHistorico() {
        evento().setVersaoDesignacao(Integer.MAX_VALUE);
        status(409, () -> service.designar(dados.eventoId(), dados.equipeAId(), Integer.MAX_VALUE, "Limite da versão", dados.operadorId()));
        assertThat(evento().getEquipeDesignada()).isNull();
        assertThat(evento().getVersaoDesignacao()).isEqualTo(Integer.MAX_VALUE);
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isZero();
    }

    private EventoOperacional evento() { return em.find(EventoOperacional.class, dados.eventoId()); }
    private void autenticar(PerfilAcesso perfil) {
        EventosTransicaoFixture.autenticar(EventosTransicaoFixture.usuario(em, perfil).getEmail());
    }
    private void status(int esperado, Runnable acao) {
        assertThat(assertThrows(ResponseStatusException.class, acao::run).getStatusCode().value()).isEqualTo(esperado);
    }
    private void semAlteracao() {
        assertThat(evento().getEquipeDesignada()).isNull();
        assertThat(evento().getVersaoDesignacao()).isZero();
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isZero();
    }
}
