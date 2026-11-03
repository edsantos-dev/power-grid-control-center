package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoDesignacaoEquipeRepository;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture.Dados;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
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
class DesignacaoEquipeControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00.123456789Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private HistoricoDesignacaoEquipeRepository historicos;
    @MockitoBean private Clock clock;
    private Dados dados;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = DesignacaoEquipeFixture.criar(em);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveExporVinculoEVersaoEmTodosOsRetornosDeEvento() throws Exception {
        mvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipeDesignadaId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.versaoDesignacao").value(0));
        var resultado = enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200);
        var evento = mapper.readValue(resultado.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
        assertThat(evento.equipeDesignadaId()).isEqualTo(dados.equipeAId());
        assertThat(evento.versaoDesignacao()).isEqualTo(1);
        assertThat(evento.situacao()).isEqualTo("ABERTO");
        mvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipeDesignadaId").value(dados.equipeAId()))
                .andExpect(jsonPath("$.versaoDesignacao").value(1));
        mvc.perform(get("/api/eventos-operacionais").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + dados.eventoId() + ")].equipeDesignadaId").value(org.hamcrest.Matchers.contains(dados.equipeAId().intValue())));
        mvc.perform(get("/api/ativos-rede/{id}/manutencoes", dados.ativoId()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].evento.equipeDesignadaId").value(dados.equipeAId()))
                .andExpect(jsonPath("$.content[0].evento.versaoDesignacao").value(1));
        em.find(EventoOperacional.class, dados.eventoId()).setCategoria(EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO);
        em.flush();
        mvc.perform(get("/api/interrupcoes/{id}", dados.eventoId()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipeDesignadaId").value(dados.equipeAId()))
                .andExpect(jsonPath("$.versaoDesignacao").value(1));
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void devePermitirGerenteOuOperadorResponsavelEAuditarSessao(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200);
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(usuario(perfil).getId()));
    }

    @Test
    void deveBloquearTecnicoMesmoQuandoResponsavelPeloEvento() throws Exception {
        em.find(EventoOperacional.class, dados.eventoId()).setResponsavelUsuario(usuario(PerfilAcesso.TECNICO_MANUTENCAO));
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 403);
        inalterado();
    }

    @Test
    void deveBloquearOperadorNaoResponsavel() throws Exception {
        em.find(EventoOperacional.class, dados.eventoId()).setResponsavelUsuario(usuario(PerfilAcesso.TECNICO_MANUTENCAO));
        autenticar(PerfilAcesso.OPERADOR);
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 403); inalterado();
    }

    @Test
    void devePreservarIdempotenciaComVersaoAtualERejeitarRepeticaoComVersaoAntiga() throws Exception {
        enviar(dados.eventoId(), corpo(null, 0), 200);
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isZero();
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200);
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 1), 200);
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 409);
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].versaoDesignacao").value(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveRetirarDesignacaoComEquipeOmitidaOuNula(boolean omitir) throws Exception {
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200);
        var body = corpo(null, 1); if (omitir) body.remove("equipeId");
        var resultado = enviar(dados.eventoId(), body, 200);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
        assertThat(dto.equipeDesignadaId()).isNull(); assertThat(dto.versaoDesignacao()).isEqualTo(2);
    }

    @Test
    void devePaginarHistoricoComOrdemFixaDesempatePorIdEIsolamentoDeOutrosEventos() throws Exception {
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200);
        enviar(dados.eventoId(), corpo(dados.equipeBId(), 1), 200);
        enviar(dados.eventoId(), corpo(null, 2), 200);
        var outro = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        enviar(outro.getId(), corpo(dados.equipeAId(), 0), 200);
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content[0].versaoDesignacao").value(3))
                .andExpect(jsonPath("$.content[1].versaoDesignacao").value(2)).andExpect(jsonPath("$.content[2].versaoDesignacao").value(1))
                .andExpect(jsonPath("$.content[0].equipeNovaId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].equipeAnteriorId").value(dados.equipeBId()));
        mvc.perform(get(base() + "/designacoes").session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].versaoDesignacao").value(2));
        mvc.perform(get(base() + "/designacoes").session(sessao).param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get(base() + "/designacoes").session(sessao).param("size", "100").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(base() + "/transicoes").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deveRetornarPaginaVaziaE404ParaReferenciasInexistentes() throws Exception {
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        enviar(Long.MAX_VALUE, corpo(dados.equipeAId(), 0), 404);
        enviar(dados.eventoId(), corpo(Long.MAX_VALUE, 0), 404);
        mvc.perform(get("/api/eventos-operacionais/{id}/designacoes", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONCLUIDO", "FORA_DE_SERVICO", "SEM_INTEGRANTES"})
    void deveRetornar409ParaImpedimentosOperacionais(String caso) throws Exception {
        if (caso.equals("CONCLUIDO")) em.find(EventoOperacional.class, dados.eventoId()).setSituacao("CONCLUIDO");
        else if (caso.equals("FORA_DE_SERVICO")) em.find(EquipeManutencao.class, dados.equipeAId()).setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        else em.createQuery("delete from VinculoEquipe v where v.equipe.id = :id").setParameter("id", dados.equipeAId()).executeUpdate();
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 409); inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"versaoAusente", "versaoNula", "versaoNegativa", "versaoFracionaria", "versaoTexto", "versaoOverflow",
            "equipeZero", "equipeNegativa", "equipeFracionaria", "equipeTexto", "comentarioAusente", "comentarioNulo", "comentarioVazio",
            "comentarioBranco", "comentarioLongo"})
    void deveRejeitarDtoInvalidoSemCoercaoSilenciosa(String caso) throws Exception {
        var body = corpo(dados.equipeAId(), 0);
        switch (caso) {
            case "versaoAusente" -> body.remove("versaoDesignacaoEsperada");
            case "versaoNula" -> body.put("versaoDesignacaoEsperada", null);
            case "versaoNegativa" -> body.put("versaoDesignacaoEsperada", -1);
            case "versaoFracionaria" -> body.put("versaoDesignacaoEsperada", new java.math.BigDecimal("0.5"));
            case "versaoTexto" -> body.put("versaoDesignacaoEsperada", "0");
            case "versaoOverflow" -> body.put("versaoDesignacaoEsperada", 2147483648L);
            case "equipeZero" -> body.put("equipeId", 0);
            case "equipeNegativa" -> body.put("equipeId", -1);
            case "equipeFracionaria" -> body.put("equipeId", new java.math.BigDecimal("1.5"));
            case "equipeTexto" -> body.put("equipeId", dados.equipeAId().toString());
            case "comentarioAusente" -> body.remove("comentario");
            case "comentarioNulo" -> body.put("comentario", null);
            case "comentarioVazio" -> body.put("comentario", "");
            case "comentarioBranco" -> body.put("comentario", "   ");
            default -> body.put("comentario", "a".repeat(1001));
        }
        enviar(dados.eventoId(), body, 400); inalterado();
    }

    @Test
    void deveAceitarComentarioNoLimiteEIgnorarAutoriaOuInstanteForjados() throws Exception {
        var body = corpo(dados.equipeAId(), 0);
        body.put("comentario", "a".repeat(1000)); body.put("usuarioId", dados.operadorId());
        body.put("registradoEm", "2000-01-01T00:00:00Z"); body.put("versaoDesignacao", 99);
        enviar(dados.eventoId(), body, 200);
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(dados.gerenteId()))
                .andExpect(jsonPath("$.content[0].versaoDesignacao").value(1));
        var h = historicos.findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(dados.eventoId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst();
        assertThat(h.getComentario()).hasSize(1000);
        assertThat(h.getRegistradoEm().toInstant()).isEqualTo(AGORA.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void deveRejeitarCorpoAusenteOuMalformado() throws Exception {
        mvc.perform(put(base() + "/equipe-designada").session(sessao).header(csrf.headerName(), csrf.token())).andExpect(status().isBadRequest());
        mvc.perform(put(base() + "/equipe-designada").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void todosOsPerfisAutenticadosPodemConsultarHistorico(PerfilAcesso perfil) throws Exception {
        enviar(dados.eventoId(), corpo(dados.equipeAId(), 0), 200); autenticar(perfil);
        mvc.perform(get(base() + "/designacoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(dados.gerenteId()));
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        var body = mapper.writeValueAsString(corpo(dados.equipeAId(), 0));
        mvc.perform(put(base() + "/equipe-designada").session(sessao).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(put(base() + "/equipe-designada").session(anonima).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/designacoes")).andExpect(status().isUnauthorized()); inalterado();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5", "sort=registradoEm,asc",
            "dataInicio=2026-01-01T00:00:00Z"})
    void deveRejeitarPaginacaoInvalidaESort(String parametro) throws Exception {
        String[] partes = parametro.split("=", 2);
        mvc.perform(get(base() + "/designacoes").session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort"})
    void deveRejeitarParametrosVaziosOuRepetidos(String campo) throws Exception {
        mvc.perform(get(base() + "/designacoes").session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/designacoes").session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadoresInvalidos(String id) throws Exception {
        mvc.perform(get("/api/eventos-operacionais/" + id + "/designacoes").session(sessao)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/eventos-operacionais/" + id + "/equipe-designada").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo(dados.equipeAId(), 0)))).andExpect(status().isBadRequest());
    }

    private String base() { return "/api/eventos-operacionais/" + dados.eventoId(); }
    private Usuario usuario(PerfilAcesso perfil) { return EventosTransicaoFixture.usuario(em, perfil); }
    private Map<String, Object> corpo(Long equipeId, int versao) {
        var body = new LinkedHashMap<String, Object>(); body.put("equipeId", equipeId);
        body.put("versaoDesignacaoEsperada", versao); body.put("comentario", "Despacho operacional"); return body;
    }
    private MvcResult enviar(Long eventoId, Map<String, Object> body, int esperado) throws Exception {
        em.flush();
        return mvc.perform(put("/api/eventos-operacionais/{id}/equipe-designada", eventoId).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().is(esperado)).andReturn();
    }
    private void inalterado() {
        assertThat(em.find(EventoOperacional.class, dados.eventoId()).getEquipeDesignada()).isNull();
        assertThat(em.find(EventoOperacional.class, dados.eventoId()).getVersaoDesignacao()).isZero();
        assertThat(historicos.countByEventoOperacionalId(dados.eventoId())).isZero();
    }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario(perfil).getEmail())
                .param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
