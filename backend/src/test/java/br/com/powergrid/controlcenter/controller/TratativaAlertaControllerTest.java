package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture.Dados;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class TratativaAlertaControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00.123456Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private HistoricoAlertaRepository historicos;
    @MockitoBean private Clock clock;
    private Dados dados;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = TratativaAlertaFixture.criar(em, SituacaoAlerta.ABERTO, true);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @ParameterizedTest
    @CsvSource({"reconhecimento,ABERTO,200,RECONHECIDO,1", "reconhecimento,RECONHECIDO,200,RECONHECIDO,0",
            "reconhecimento,RESOLVIDO,409,RESOLVIDO,0", "resolucao,ABERTO,409,ABERTO,0",
            "resolucao,RECONHECIDO,200,RESOLVIDO,1", "resolucao,RESOLVIDO,200,RESOLVIDO,0"})
    void deveAplicarMaquinaDeEstadosEIdempotenciaInclusiveEmLegados(String acao, SituacaoAlerta origem,
            int codigo, SituacaoAlerta destino, long quantidade) throws Exception {
        em.find(Alerta.class, dados.alertaId()).setSituacao(origem); em.flush();
        var resposta = enviar(dados.alertaId(), acao, corpo("Tratativa"), codigo);
        if (codigo == 200) assertThat(dto(resposta).situacao()).isEqualTo(destino);
        assertThat(historicos.countByAlertaId(dados.alertaId())).isEqualTo(quantidade);
        assertThat(em.find(Alerta.class, dados.alertaId()).getSituacao()).isEqualTo(destino);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void devePermitirPerfisOperacionaisEAuditarUsuarioRealDaSessao(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var body = corpo("Reconhecimento válido");
        body.put("usuarioId", usuario(PerfilAcesso.TECNICO_MANUTENCAO).getId()); body.put("alteradoEm", "2000-01-01T00:00:00Z");
        body.put("situacao", "RESOLVIDO");
        var alerta = dto(enviar(dados.alertaId(), "reconhecimento", body, 200));
        assertThat(alerta.reconhecidoPorUsuarioId()).isEqualTo(usuario(perfil).getId());
        assertThat(alerta.reconhecidoEm().toInstant()).isEqualTo(AGORA);
        assertThat(alerta.resolvidoEm()).isNull(); assertThat(alerta.resolvidoPorUsuarioId()).isNull();
        var resolvido = dto(enviar(dados.alertaId(), "resolucao", corpo("Resolução válida"), 200));
        assertThat(resolvido.resolvidoPorUsuarioId()).isEqualTo(usuario(perfil).getId());
        assertThat(resolvido.resolvidoEm().toInstant()).isEqualTo(AGORA);
    }

    @Test
    void deveBloquearTecnicoNasDuasAcoes() throws Exception {
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        enviar(dados.alertaId(), "reconhecimento", corpo("Sem permissão"), 403);
        enviar(dados.alertaId(), "resolucao", corpo("Sem permissão"), 403);
        assertThat(historicos.countByAlertaId(dados.alertaId())).isZero();
    }

    @Test
    void devePreservarPrimeiraAuditoriaEmRepeticoes() throws Exception {
        var reconhecido = dto(enviar(dados.alertaId(), "reconhecimento", corpo("Primeiro reconhecimento"), 200));
        autenticar(PerfilAcesso.GERENTE_OPERACOES); when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        var repetido = dto(enviar(dados.alertaId(), "reconhecimento", corpo("Comentário substituto"), 200));
        assertThat(repetido.reconhecidoEm()).isEqualTo(reconhecido.reconhecidoEm());
        assertThat(repetido.reconhecidoPorUsuarioId()).isEqualTo(dados.operadorId());
        var resolvido = dto(enviar(dados.alertaId(), "resolucao", corpo("Primeira resolução"), 200));
        autenticar(PerfilAcesso.OPERADOR); when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
        var repetida = dto(enviar(dados.alertaId(), "resolucao", corpo("Outra resolução"), 200));
        assertThat(repetida.resolvidoEm()).isEqualTo(resolvido.resolvidoEm());
        assertThat(repetida.resolvidoPorUsuarioId()).isEqualTo(dados.gerenteId());
        assertThat(historicos.countByAlertaId(dados.alertaId())).isEqualTo(2);
    }

    @Test
    void deveConsultarResumoEmLoteEHistoricoIsoladoComPaginacaoFixa() throws Exception {
        enviar(dados.alertaId(), "reconhecimento", corpo("Reconhecido"), 200);
        var outro = TratativaAlertaFixture.criar(em, SituacaoAlerta.ABERTO, false);
        enviar(outro.alertaId(), "reconhecimento", corpo("Outro alerta"), 200);
        mvc.perform(get("/api/alertas").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + dados.alertaId() + ")].reconhecidoPorUsuarioId")
                        .value(org.hamcrest.Matchers.contains(dados.operadorId().intValue())))
                .andExpect(jsonPath("$[?(@.id == " + outro.alertaId() + ")].reconhecidoPorUsuarioId")
                        .value(org.hamcrest.Matchers.contains(dados.operadorId().intValue())));
        enviar(dados.alertaId(), "resolucao", corpo("Resolvido"), 200);
        mvc.perform(get(base() + "/historico").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].situacaoNova").value("RESOLVIDO"))
                .andExpect(jsonPath("$.content[1].situacaoNova").value("RECONHECIDO"));
        mvc.perform(get(base() + "/historico").session(sessao).param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false)).andExpect(jsonPath("$.content[0].comentario").value("Resolvido"));
        mvc.perform(get(base() + "/historico").session(sessao).param("size", "1").param("page", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.last").value(true)).andExpect(jsonPath("$.content[0].comentario").value("Reconhecido"));
        mvc.perform(get(base() + "/historico").session(sessao).param("size", "100").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("RESOLVIDO"))
                .andExpect(jsonPath("$.resolvidoPorUsuarioId").value(dados.operadorId()));
    }

    @Test
    void deveManterAusenciaDeAuditoriaLegadaExplicita() throws Exception {
        em.find(Alerta.class, dados.alertaId()).setSituacao(SituacaoAlerta.RESOLVIDO); em.flush();
        mvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("RESOLVIDO"))
                .andExpect(jsonPath("$.reconhecidoEm").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.reconhecidoPorUsuarioId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resolvidoEm").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resolvidoPorUsuarioId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(base() + "/historico").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deveRetornar404ParaAlertaInexistente() throws Exception {
        enviar(Long.MAX_VALUE, "reconhecimento", corpo("Ausente"), 404);
        enviar(Long.MAX_VALUE, "resolucao", corpo("Ausente"), 404);
        mvc.perform(get("/api/alertas/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(get("/api/alertas/{id}/historico", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reconhecimento", "resolucao"})
    void deveRejeitarComentarioAusenteNuloBrancoOuAcimaDoLimite(String acao) throws Exception {
        enviar(dados.alertaId(), acao, Map.of(), 400);
        enviar(dados.alertaId(), acao, corpo(null), 400);
        enviar(dados.alertaId(), acao, corpo(""), 400);
        enviar(dados.alertaId(), acao, corpo("   "), 400);
        enviar(dados.alertaId(), acao, corpo("a".repeat(1001)), 400);
        assertThat(historicos.countByAlertaId(dados.alertaId())).isZero();
    }

    @Test
    void deveAceitarComentarioComMilCaracteres() throws Exception {
        enviar(dados.alertaId(), "reconhecimento", corpo("a".repeat(1000)), 200);
        assertThat(historicos.buscarPorAlertaOrdenado(dados.alertaId(), 1, 0).getFirst().getComentario()).hasSize(1000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reconhecimento", "resolucao"})
    void deveRejeitarCorpoAusenteOuMalformado(String acao) throws Exception {
        mvc.perform(patch(base() + "/" + acao).session(sessao).header(csrf.headerName(), csrf.token())).andExpect(status().isBadRequest());
        mvc.perform(patch(base() + "/" + acao).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirLeituraParaTodosOsAutenticados(PerfilAcesso perfil) throws Exception {
        enviar(dados.alertaId(), "reconhecimento", corpo("Auditoria"), 200); autenticar(perfil);
        mvc.perform(get(base()).session(sessao)).andExpect(status().isOk());
        mvc.perform(get(base() + "/historico").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        for (String acao : new String[] {"reconhecimento", "resolucao"}) {
            mvc.perform(patch(base() + "/" + acao).session(sessao).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(corpo("Sem CSRF")))).andExpect(status().isForbidden());
        }
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(patch(base() + "/reconhecimento").session(anonima).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo("Sem sessão")))).andExpect(status().isUnauthorized());
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/historico")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5",
            "sort=alteradoEm,asc", "dataInicio=2026-01-01T00:00:00Z"})
    void deveRejeitarParametrosInvalidosESort(String parametro) throws Exception {
        var partes = parametro.split("=", 2);
        mvc.perform(get(base() + "/historico").session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort"})
    void deveRejeitarParametrosVaziosOuRepetidos(String parametro) throws Exception {
        mvc.perform(get(base() + "/historico").session(sessao).param(parametro, "")).andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/historico").session(sessao).param(parametro, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadoresInvalidos(String id) throws Exception {
        mvc.perform(get("/api/alertas/" + id + "/historico").session(sessao)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alertas/" + id).session(sessao)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/alertas/" + id + "/reconhecimento").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo("Identificador inválido"))))
                .andExpect(status().isBadRequest());
    }

    private String base() { return "/api/alertas/" + dados.alertaId(); }
    private Map<String, Object> corpo(String comentario) {
        var body = new LinkedHashMap<String, Object>(); body.put("comentario", comentario); return body;
    }
    private AlertaResponseDTO dto(MvcResult resposta) throws Exception {
        return mapper.readValue(resposta.getResponse().getContentAsString(), AlertaResponseDTO.class);
    }
    private MvcResult enviar(Long id, String acao, Map<String, ?> body, int esperado) throws Exception {
        em.flush();
        return mvc.perform(patch("/api/alertas/" + id + "/" + acao).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().is(esperado)).andReturn();
    }
    private Usuario usuario(PerfilAcesso perfil) { return EventosTransicaoFixture.usuario(em, perfil); }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario(perfil).getEmail()).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
