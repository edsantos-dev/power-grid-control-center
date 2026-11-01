package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
class TransacaoEventoControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;
    @Autowired private HistoricoEventoOperacionalRepository historicos;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00.123456789Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Long eventoId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        eventoId = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR).getId();
        autenticar(PerfilAcesso.OPERADOR);
    }

    @Test
    void deveCriarDuasTransicoesComLocationAuditoriaEDatasEfetivas() throws Exception {
        var result = enviar(eventoId, payload("ABERTO", "EM_ANDAMENTO"), 201);
        var inicio = dto(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost" + base(eventoId) + "/" + inicio.id());
        assertThat(inicio.eventoId()).isEqualTo(eventoId);
        assertThat(inicio.usuarioId()).isEqualTo(usuario(PerfilAcesso.OPERADOR).getId());
        assertThat(inicio.situacaoAnterior()).isEqualTo(SituacaoEventoOperacional.ABERTO);
        assertThat(inicio.situacaoNova()).isEqualTo(SituacaoEventoOperacional.EM_ANDAMENTO);
        assertThat(inicio.comentario()).isEqualTo("Justificativa operacional");
        assertThat(inicio.alteradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(ChronoUnit.MICROS));
        assertThat(inicio.alteradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        mockMvc.perform(get(result.getResponse().getHeader("Location")).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
        when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        var concluido = dto(enviar(eventoId, payload("EM_ANDAMENTO", "CONCLUIDO"), 201));
        assertThat(concluido.alteradoEm().toInstant()).isEqualTo(AGORA.plusSeconds(60).truncatedTo(ChronoUnit.MICROS));
        em.clear();
        var evento = em.find(EventoOperacional.class, eventoId);
        assertThat(evento.getSituacao()).isEqualTo("CONCLUIDO");
        assertThat(evento.getInicioEfetivo().toInstant()).isEqualTo(inicio.alteradoEm().toInstant());
        assertThat(evento.getTerminoEfetivo().toInstant()).isEqualTo(concluido.alteradoEm().toInstant());
        mockMvc.perform(get("/api/eventos-operacionais/{id}", eventoId).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDO"));
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirResponsavelEGerente(PerfilAcesso perfil) throws Exception {
        Long alvo = perfil == PerfilAcesso.TECNICO_MANUTENCAO
                ? EventosTransicaoFixture.evento(em, perfil).getId() : eventoId;
        autenticar(perfil);
        var req = payload("ABERTO", "EM_ANDAMENTO"); req.put("comentario", "a".repeat(1000));
        assertThat(dto(enviar(alvo, req, 201)).usuarioId()).isEqualTo(usuario(perfil).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveRetornar403ParaUsuarioQueNaoEhResponsavel(PerfilAcesso perfil) throws Exception {
        Long alvo = perfil == PerfilAcesso.OPERADOR
                ? EventosTransicaoFixture.evento(em, PerfilAcesso.TECNICO_MANUTENCAO).getId() : eventoId;
        autenticar(perfil);
        enviar(alvo, payload("ABERTO", "EM_ANDAMENTO"), 403);
        inalterado(alvo, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"destinoNulo", "esperadaNula", "comentarioNulo", "comentarioVazio", "comentarioBranco", "comentarioLongo", "estadoInvalido"})
    void deveRetornar400ParaDtoInvalido(String caso) throws Exception {
        var req = payload("ABERTO", "EM_ANDAMENTO");
        switch (caso) {
            case "destinoNulo" -> req.put("situacaoDestino", null);
            case "esperadaNula" -> req.put("situacaoEsperada", null);
            case "comentarioNulo" -> req.put("comentario", null);
            case "comentarioVazio" -> req.put("comentario", "");
            case "comentarioBranco" -> req.put("comentario", " 	 ");
            case "comentarioLongo" -> req.put("comentario", "a".repeat(1001));
            default -> req.put("situacaoDestino", "RESOLVIDO");
        }
        enviar(eventoId, req, 400); inalterado(eventoId, 0);
    }

    @Test
    void deveRejeitarCorpoAusenteOuJsonMalformado() throws Exception {
        mockMvc.perform(post(base(eventoId)).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(base(eventoId)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()); inalterado(eventoId, 0);
    }

    @Test
    void deveRetornar409ParaSituacaoDivergenteSaltoERepeticao() throws Exception {
        enviar(eventoId, payload("EM_ANDAMENTO", "CONCLUIDO"), 409);
        enviar(eventoId, payload("ABERTO", "CONCLUIDO"), 409);
        enviar(eventoId, payload("ABERTO", "ABERTO"), 409);
        inalterado(eventoId, 0);
        enviar(eventoId, payload("ABERTO", "EM_ANDAMENTO"), 201);
        enviar(eventoId, payload("ABERTO", "EM_ANDAMENTO"), 409);
        em.clear(); assertThat(em.find(EventoOperacional.class, eventoId).getSituacao()).isEqualTo("EM_ANDAMENTO");
        assertThat(historicos.countByEventoId(eventoId)).isEqualTo(1);
    }

    @Test
    void deveRetornar404ParaEventoOuTransicaoAusentesEParaDetalheDeOutroEvento() throws Exception {
        enviar(Long.MAX_VALUE, payload("ABERTO", "EM_ANDAMENTO"), 404);
        mockMvc.perform(get(base(Long.MAX_VALUE)).session(sessao)).andExpect(status().isNotFound());
        mockMvc.perform(get(base(Long.MAX_VALUE) + "/1").session(sessao)).andExpect(status().isNotFound());
        mockMvc.perform(get(base(eventoId) + "/" + Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        Long outro = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR).getId();
        var transicao = dto(enviar(outro, payload("ABERTO", "EM_ANDAMENTO"), 201));
        mockMvc.perform(get(base(eventoId) + "/" + transicao.id()).session(sessao))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(base(eventoId)).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void devePaginarComOrdemFixaDesempatePorIdEIsolamentoPorEvento() throws Exception {
        var primeira = dto(enviar(eventoId, payload("ABERTO", "EM_ANDAMENTO"), 201));
        var segunda = dto(enviar(eventoId, payload("EM_ANDAMENTO", "CONCLUIDO"), 201));
        Long outro = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR).getId();
        enviar(outro, payload("ABERTO", "EM_ANDAMENTO"), 201); em.clear();
        mockMvc.perform(get(base(eventoId)).session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(segunda.id()))
                .andExpect(jsonPath("$.content[1].id").value(primeira.id()));
        mockMvc.perform(get(base(eventoId)).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].id").value(segunda.id()));
        mockMvc.perform(get(base(eventoId)).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].id").value(primeira.id()));
        mockMvc.perform(get(base(eventoId)).session(sessao).param("size", "100").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5", "sort=alteradoEm,asc", "dataInicio=2026-01-01T00:00:00Z"})
    void deveRejeitarPaginacaoInvalidaOrdenacaoInjetadaEParametrosNaoSuportados(String parametro) throws Exception {
        String[] partes = parametro.split("=", 2);
        mockMvc.perform(get(base(eventoId)).session(sessao).param(partes[0], partes[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort"})
    void deveRejeitarParametrosVaziosOuDuplicados(String campo) throws Exception {
        mockMvc.perform(get(base(eventoId)).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base(eventoId)).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveExigirSessaoECsrfNasTransicoes() throws Exception {
        var body = objectMapper.writeValueAsString(payload("ABERTO", "EM_ANDAMENTO"));
        mockMvc.perform(post(base(eventoId)).session(sessao).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post(base(eventoId)).session(anonima).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base(eventoId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base(eventoId) + "/1")).andExpect(status().isUnauthorized());
        inalterado(eventoId, 0);
    }

    @Test
    void deveIgnorarTentativaDeForjarUsuarioEInstanteNoCorpo() throws Exception {
        var req = payload("ABERTO", "EM_ANDAMENTO"); req.put("usuarioId", usuario(PerfilAcesso.GERENTE_OPERACOES).getId());
        req.put("alteradoEm", "2000-01-01T00:00:00Z");
        var response = dto(enviar(eventoId, req, 201));
        assertThat(response.usuarioId()).isEqualTo(usuario(PerfilAcesso.OPERADOR).getId());
        assertThat(response.alteradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(ChronoUnit.MICROS));
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultaAutenticadaDoHistoricoParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        var transicao = dto(enviar(eventoId, payload("ABERTO", "EM_ANDAMENTO"), 201)); autenticar(perfil);
        mockMvc.perform(get(base(eventoId)).session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base(eventoId) + "/" + transicao.id()).session(sessao)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadoresInvalidos(String id) throws Exception {
        mockMvc.perform(get("/api/eventos-operacionais/" + id + "/transicoes").session(sessao)).andExpect(status().isBadRequest());
        mockMvc.perform(get(base(eventoId) + "/" + id).session(sessao)).andExpect(status().isBadRequest());
    }

    private MvcResult enviar(Long evento, Map<String, Object> body, int esperado) throws Exception {
        return mockMvc.perform(post(base(evento)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private Map<String, Object> payload(String esperada, String destino) {
        var body = new LinkedHashMap<String, Object>(); body.put("situacaoEsperada", esperada);
        body.put("situacaoDestino", destino); body.put("comentario", "Justificativa operacional"); return body;
    }

    private HistoricoEventoResponseDTO dto(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
    }

    private void inalterado(Long id, long quantidade) {
        em.clear(); assertThat(em.find(EventoOperacional.class, id).getSituacao()).isEqualTo("ABERTO");
        assertThat(historicos.countByEventoId(id)).isEqualTo(quantidade);
    }

    private String base(Long id) { return "/api/eventos-operacionais/" + id + "/transicoes"; }
    private Usuario usuario(PerfilAcesso perfil) { return EventosTransicaoFixture.usuario(em, perfil); }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario(perfil).getEmail())
                .param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
