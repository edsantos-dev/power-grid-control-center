package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture.Dados;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class TratativaAlertaServiceTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00.123456789Z");
    @Autowired private TratativaAlertaService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HistoricoAlertaRepository historicos;
    @MockitoBean private Clock clock;
    private Dados dados;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = TratativaAlertaFixture.criar(em, SituacaoAlerta.ABERTO, true);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveExecutarCicloCompletoComAuditoriaSemModificarCadastroOuAtivo(boolean comAtivo) {
        var d = comAtivo ? dados : TratativaAlertaFixture.criar(em, SituacaoAlerta.ABERTO, false);
        var original = em.find(Alerta.class, d.alertaId());
        var gerado = original.getGeradoEm().toInstant();
        var reconhecido = service.reconhecer(d.alertaId(), d.operadorId(), "Ocorrência reconhecida");
        assertThat(reconhecido.getSituacao()).isEqualTo(SituacaoAlerta.RECONHECIDO);
        assertThat(jdbc.queryForObject("SELECT status_origem FROM vw_alertas_dashboard WHERE tipo_alerta = 'GENERICO' AND id_origem = ?",
                String.class, d.alertaId())).isEqualTo("RECONHECIDO");
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        var resolvido = service.resolver(d.alertaId(), d.gerenteId(), "Ocorrência resolvida");
        assertThat(resolvido.getSituacao()).isEqualTo(SituacaoAlerta.RESOLVIDO);
        assertThat(resolvido.getTipo()).isEqualTo("Ocorrência operacional");
        assertThat(resolvido.getCriticidade()).isEqualTo(Criticidade.ALTA);
        assertThat(resolvido.getGeradoEm().toInstant()).isEqualTo(gerado);
        assertThat(resolvido.getEscopo()).isEqualTo(comAtivo ? EscopoAlerta.ATIVO : EscopoAlerta.REDE_MONITORADA);
        if (comAtivo) {
            assertThat(resolvido.getAtivo().getId()).isEqualTo(d.ativoId());
            assertThat(em.find(Subestacao.class, d.ativoId()).getEstadoOperacional()).isEqualTo(AtivoRede.EstadoOperacional.OPERACIONAL);
        } else assertThat(resolvido.getAtivo()).isNull();
        em.clear();
        var lista = historicos.findByAlertaIdOrderByAlteradoEmDescIdDesc(d.alertaId(), PageRequest.of(0, 20)).getContent();
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).getSituacaoAnterior()).isEqualTo(SituacaoAlerta.RECONHECIDO);
        assertThat(lista.get(0).getSituacaoNova()).isEqualTo(SituacaoAlerta.RESOLVIDO);
        assertThat(lista.get(0).getUsuario().getId()).isEqualTo(d.gerenteId());
        assertThat(lista.get(0).getComentario()).isEqualTo("Ocorrência resolvida");
        assertThat(lista.get(0).getAlteradoEm().toInstant()).isEqualTo(AGORA.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(lista.get(1).getSituacaoAnterior()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(lista.get(1).getSituacaoNova()).isEqualTo(SituacaoAlerta.RECONHECIDO);
        assertThat(lista.get(1).getUsuario().getId()).isEqualTo(d.operadorId());
        assertThat(lista.get(1).getComentario()).isEqualTo("Ocorrência reconhecida");
        assertThat(lista.get(1).getAlteradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(lista).allSatisfy(h -> assertThat(h.getAlteradoEm().getOffset()).isEqualTo(ZoneOffset.UTC));
    }

    @ParameterizedTest
    @CsvSource({"RECONHECER,ABERTO,200,RECONHECIDO,1", "RECONHECER,RECONHECIDO,200,RECONHECIDO,0",
            "RECONHECER,RESOLVIDO,409,RESOLVIDO,0", "RESOLVER,ABERTO,409,ABERTO,0",
            "RESOLVER,RECONHECIDO,200,RESOLVIDO,1", "RESOLVER,RESOLVIDO,200,RESOLVIDO,0"})
    void deveAplicarMaquinaDeEstadosEIdempotenciaSemInventarHistoricoLegado(String acao, SituacaoAlerta origem,
            int esperado, SituacaoAlerta destino, long registros) {
        var alerta = em.find(Alerta.class, dados.alertaId()); alerta.setSituacao(origem); em.flush();
        if (esperado == 409) status(409, () -> executar(acao, dados.alertaId(), dados.operadorId(), "Tratativa"));
        else assertThat(executar(acao, dados.alertaId(), dados.operadorId(), "Tratativa").getSituacao()).isEqualTo(destino);
        assertThat(situacao()).isEqualTo(destino.name());
        assertThat(historicos.countByAlertaId(dados.alertaId())).isEqualTo(registros);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeticaoDevePreservarPrimeiroAutorInstanteEComentario(boolean resolver) {
        service.reconhecer(dados.alertaId(), dados.operadorId(), "Primeiro reconhecimento");
        if (resolver) service.resolver(dados.alertaId(), dados.operadorId(), "Primeira resolução");
        var antes = historicos.buscarPorAlertaOrdenado(dados.alertaId(), 20, 0);
        var destino = resolver ? SituacaoAlerta.RESOLVIDO : SituacaoAlerta.RECONHECIDO;
        var primeiro = antes.stream().filter(h -> h.getSituacaoNova() == destino).findFirst().orElseThrow();
        Long id = primeiro.getId(); var instante = primeiro.getAlteradoEm().toInstant(); String comentario = primeiro.getComentario();
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
        assertThat(executar(resolver ? "RESOLVER" : "RECONHECER", dados.alertaId(), dados.gerenteId(), "Tentativa de substituir auditoria")
                .getSituacao()).isEqualTo(destino);
        em.clear();
        assertThat(historicos.countByAlertaId(dados.alertaId())).isEqualTo(resolver ? 2 : 1);
        var preservado = historicos.findById(id).orElseThrow();
        assertThat(preservado.getUsuario().getId()).isEqualTo(dados.operadorId());
        assertThat(preservado.getAlteradoEm().toInstant()).isEqualTo(instante);
        assertThat(preservado.getComentario()).isEqualTo(comentario);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void devePermitirPerfisOperacionaisSemExigirResponsavelNoAlerta(PerfilAcesso perfil) {
        autenticar(perfil);
        Long id = EventosTransicaoFixture.usuario(em, perfil).getId();
        assertThat(service.reconhecer(dados.alertaId(), id, "Atendimento autorizado").getSituacao()).isEqualTo(SituacaoAlerta.RECONHECIDO);
    }

    @Test
    void deveBloquearTecnicoMesmoComAuthorityDeGerenteNoPrincipal() {
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        assertThrows(AccessDeniedException.class, () -> service.reconhecer(dados.alertaId(), dados.tecnicoId(), "Sem permissão"));
        inalterado();
    }

    @Test
    void deveBloquearIdentidadeForjada() {
        assertThrows(AccessDeniedException.class, () -> service.reconhecer(dados.alertaId(), dados.gerenteId(), "Autor divergente"));
        inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUSENTE", "ANONIMO", "NAO_AUTENTICADO", "INEXISTENTE"})
    void deveExigirUsuarioDaSessaoValido(String caso) {
        SecurityContextHolder.clearContext();
        if (caso.equals("ANONIMO")) SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("teste", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        if (caso.equals("NAO_AUTENTICADO")) SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("operador", ""));
        if (caso.equals("INEXISTENTE")) EventosTransicaoFixture.autenticar("inexistente@powergrid.local");
        status(401, () -> service.reconhecer(dados.alertaId(), dados.operadorId(), "Sem sessão"));
        inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECONHECER", "RESOLVER"})
    void deveRetornar404ParaAlertaInexistente(String acao) {
        status(404, () -> executar(acao, Long.MAX_VALUE, dados.operadorId(), "Alerta inexistente"));
        inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULA", "IMPORTADO"})
    void deveRetornar409ParaSituacoesLegadasSemConversaoInvalidaDoEnum(String situacao) {
        String valor = situacao.equals("NULA") ? null : situacao;
        jdbc.update("UPDATE alerta SET situacao = ? WHERE id = ?", valor, dados.alertaId()); em.clear();
        status(409, () -> service.reconhecer(dados.alertaId(), dados.operadorId(), "Exige regularização"));
        assertThat(situacao()).isEqualTo(valor);
        assertThat(historicos.countByAlertaId(dados.alertaId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALERTA_NULO", "ALERTA_ZERO", "USUARIO_NULO", "USUARIO_ZERO",
            "COMENTARIO_NULO", "COMENTARIO_VAZIO", "COMENTARIO_BRANCO", "COMENTARIO_LONGO"})
    void deveRejeitarEntradaInvalidaAntesDeAlterarDados(String caso) {
        Long alerta = caso.equals("ALERTA_NULO") ? null : caso.equals("ALERTA_ZERO") ? 0L : dados.alertaId();
        Long usuario = caso.equals("USUARIO_NULO") ? null : caso.equals("USUARIO_ZERO") ? 0L : dados.operadorId();
        String comentario = switch (caso) {
            case "COMENTARIO_NULO" -> null;
            case "COMENTARIO_VAZIO" -> "";
            case "COMENTARIO_BRANCO" -> "  ";
            case "COMENTARIO_LONGO" -> "a".repeat(1001);
            default -> "Tratativa";
        };
        status(400, () -> service.reconhecer(alerta, usuario, comentario));
        inalterado();
    }

    @Test
    void deveAceitarComentarioNoLimiteDeMilCaracteres() {
        service.reconhecer(dados.alertaId(), dados.operadorId(), "a".repeat(1000));
        assertThat(historicos.buscarPorAlertaOrdenado(dados.alertaId(), 1, 0).getFirst().getComentario()).hasSize(1000);
    }

    private Alerta executar(String acao, Long alertaId, Long usuarioId, String comentario) {
        return acao.equals("RECONHECER") ? service.reconhecer(alertaId, usuarioId, comentario) : service.resolver(alertaId, usuarioId, comentario);
    }
    private String situacao() {
        return jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, dados.alertaId());
    }
    private void autenticar(PerfilAcesso perfil) {
        EventosTransicaoFixture.autenticar(EventosTransicaoFixture.usuario(em, perfil).getEmail());
    }
    private void status(int codigo, Runnable acao) {
        assertThat(assertThrows(ResponseStatusException.class, acao::run).getStatusCode().value()).isEqualTo(codigo);
    }
    private void inalterado() {
        assertThat(situacao()).isEqualTo("ABERTO");
        assertThat(historicos.countByAlertaId(dados.alertaId())).isZero();
    }
}
