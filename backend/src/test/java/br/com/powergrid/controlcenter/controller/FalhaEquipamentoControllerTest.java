package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
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
class FalhaEquipamentoControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-04T12:00:00.123456Z");
    private static final String URI = "/api/falhas-equipamento";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Subestacao ativo, outro;
    private Long responsavel;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        ativo = sub(); outro = sub(); em.flush();
        responsavel = usuario(PerfilAcesso.OPERADOR).getId();
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void deveCriarComLocationComposicaoEAutoriaDaSessao(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var corpo = corpo(ativo.getId()); corpo.put("descricao", "  Defeito confirmado  ");
        corpo.put("situacao", "CONCLUIDO"); corpo.put("estadoAposReparo", "OPERACIONAL");
        corpo.put("registradorUsuarioId", usuario(PerfilAcesso.TECNICO_MANUTENCAO).getId());
        var result = postar(corpo, 201);
        var dto = resposta(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost" + URI + "/" + dto.id());
        assertThat(dto.id()).isEqualTo(dto.evento().id());
        assertThat(dto.evento().ativoId()).isEqualTo(ativo.getId());
        assertThat(dto.evento().categoria()).isEqualTo(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        assertThat(dto.evento().descricao()).isEqualTo("Defeito confirmado");
        assertThat(dto.evento().registradorUsuarioId()).isEqualTo(usuario(perfil).getId());
        assertThat(dto.evento().responsavelUsuarioId()).isEqualTo(responsavel);
        assertThat(dto.evento().situacao()).isEqualTo("ABERTO");
        assertThat(dto.estadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(dto.estadoAposReparo()).isNull(); assertThat(dto.evento().estadoAposReparo()).isNull();
        assertThat(dto.registradoEm().toInstant()).isEqualTo(AGORA);
        assertThat(dto.registradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.estadoOperacionalAtual()).isEqualTo(EstadoOperacional.FALHA);
        var detalhe = mvc.perform(get(URI + "/{id}", dto.id()).session(sessao)).andExpect(status().isOk()).andReturn();
        assertThat(resposta(detalhe)).isEqualTo(dto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "responsavelUsuarioId", "descricao"})
    void deveRejeitarCamposObrigatoriosAusentesOuNulos(String campo) throws Exception {
        var corpo = corpo(ativo.getId()); corpo.remove(campo); postar(corpo, 400);
        corpo.put(campo, null); postar(corpo, 400);
        assertThat(ativo.getEstadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
    }

    @ParameterizedTest
    @CsvSource(value = {"ativoId|0", "ativoId|-1", "ativoId|abc", "responsavelUsuarioId|0", "responsavelUsuarioId|-1",
            "criticidade|OUTRA", "criticidade|alta", "descricao|   "}, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    void deveRejeitarPayloadInvalido(String campo, String valor) throws Exception {
        var corpo = corpo(ativo.getId()); corpo.put(campo, valor); postar(corpo, 400);
    }

    @Test
    void deveAceitarCriticidadeOpcionalERejeitarDuplicidade() throws Exception {
        var corpo = corpo(ativo.getId()); corpo.remove("criticidade");
        assertThat(resposta(postar(corpo, 201)).evento().criticidade()).isNull();
        postar(corpo, 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "responsavelUsuarioId"})
    void deveRetornar404ParaReferenciaInexistente(String campo) throws Exception {
        var corpo = corpo(ativo.getId()); corpo.put(campo, Long.MAX_VALUE); postar(corpo, 404);
    }

    @Test
    void deveExigirSessaoCsrfEPerfilAutorizadoNoPost() throws Exception {
        mvc.perform(post(URI).session(sessao).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo(ativo.getId()))))
                .andExpect(status().isForbidden());
        mvc.perform(get(URI)).andExpect(status().isUnauthorized());
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO); postar(corpo(ativo.getId()), 403);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirLeituraParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        var dto = resposta(postar(corpo(ativo.getId()), 201));
        autenticar(perfil);
        mvc.perform(get(URI).session(sessao)).andExpect(status().isOk());
        mvc.perform(get(URI + "/{id}", dto.id()).session(sessao)).andExpect(status().isOk());
    }

    @Test
    void devePaginarFiltrarEOrdenarPorRegistroEIdComDesempate() throws Exception {
        var a = resposta(postar(corpo(ativo.getId()), 201));
        var b = resposta(postar(corpo(outro.getId()), 201));
        mvc.perform(get(URI).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(b.id()));
        mvc.perform(get(URI).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(a.id())).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get(URI).session(sessao).param("ativoId", ativo.getId().toString()).param("situacao", "ABERTO")
                .param("dataInicio", "2026-11-04T09:00:00.123456-03:00").param("dataFim", AGORA.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(a.id()));
        mvc.perform(get(URI).session(sessao).param("dataFim", AGORA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(URI).session(sessao).param("situacao", "CONCLUIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void deveRetornarPaginaVaziaPadraoEParaPaginaDistante() throws Exception {
        mvc.perform(get(URI).session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0));
        postar(corpo(ativo.getId()), 201);
        mvc.perform(get(URI).session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(0));
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "size|0", "size|101", "page|2147483648", "ativoId|-1", "ativoId|abc", "ativoId|9223372036854775808",
            "situacao|OUTRA", "situacao|aberto", "sort|id,asc", "outro|1", "dataInicio|invalido", "dataFim|2026-11-04T12:00:00"}, delimiter = '|')
    void deveRejeitarFiltroInvalido(String campo, String valor) throws Exception {
        mvc.perform(get(URI).session(sessao).param(campo, valor)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "ativoId", "situacao", "dataInicio", "dataFim"})
    void deveRejeitarFiltrosVaziosOuRepetidos(String campo) throws Exception {
        mvc.perform(get(URI).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mvc.perform(get(URI).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarIntervaloInvertidoEEventoForaDoDominio() throws Exception {
        mvc.perform(get(URI).session(sessao).param("dataInicio", AGORA.toString()).param("dataFim", AGORA.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URI + "/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        var e = new EventoOperacional(); e.setAtivo(ativo); e.setCategoria(EventoOperacional.Categoria.MANUTENCAO);
        e.setRegistradorUsuario(usuario(PerfilAcesso.OPERADOR)); e.setResponsavelUsuario(usuario(PerfilAcesso.OPERADOR));
        e.setSituacao("ABERTO"); em.persist(e); em.flush();
        mvc.perform(get(URI + "/{id}", e.getId()).session(sessao)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERACIONAL", "MANUTENCAO"})
    void deveConcluirFalhaPelaTransicaoExistenteEExporEstadoNosEventos(String destino) throws Exception {
        var f = resposta(postar(corpo(ativo.getId()), 201));
        transicionar(f.id(), Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Atendimento"), 201);
        transicionar(f.id(), Map.of("situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO", "comentario", "Reparo",
                "estadoAposReparo", destino), 201); em.clear();
        mvc.perform(get(URI + "/{id}", f.id()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoAposReparo").value(destino)).andExpect(jsonPath("$.estadoOperacionalAtual").value(destino))
                .andExpect(jsonPath("$.evento.situacao").value("CONCLUIDO")).andExpect(jsonPath("$.evento.estadoAposReparo").value(destino));
        mvc.perform(get("/api/eventos-operacionais/{id}", f.id()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoAposReparo").value(destino));
        mvc.perform(get("/api/eventos-operacionais").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoAposReparo").value(destino));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "nulo", "FALHA", "OUTRO"})
    void deveRejeitarConclusaoSemEstadoPermitido(String caso) throws Exception {
        var f = resposta(postar(corpo(ativo.getId()), 201));
        transicionar(f.id(), Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Atendimento"), 201);
        var corpo = new HashMap<String, Object>(); corpo.put("situacaoEsperada", "EM_ANDAMENTO"); corpo.put("situacaoDestino", "CONCLUIDO"); corpo.put("comentario", "Fim");
        if (!caso.equals("ausente")) corpo.put("estadoAposReparo", caso.equals("nulo") ? null : caso);
        transicionar(f.id(), corpo, 400);
        assertThat(ativo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @Test
    void consultaNaoDeveGerarNMaisUm() throws Exception {
        postar(corpo(ativo.getId()), 201); postar(corpo(outro.getId()), 201); em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); boolean habilitado = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long sql = stats.getPrepareStatementCount(); long fetch = stats.getEntityFetchCount();
            mvc.perform(get(URI).session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
            assertThat(stats.getPrepareStatementCount() - sql).isEqualTo(2);
            assertThat(stats.getEntityFetchCount() - fetch).isZero();
        } finally { stats.setStatisticsEnabled(habilitado); }
    }

    private MvcResult postar(Map<String, Object> corpo, int esperado) throws Exception {
        return mvc.perform(post(URI).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().is(esperado)).andReturn();
    }
    private void transicionar(Long id, Map<String, Object> corpo, int esperado) throws Exception {
        mvc.perform(post("/api/eventos-operacionais/{id}/transicoes", id).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo))).andExpect(status().is(esperado));
    }
    private FalhaEquipamentoResponseDTO resposta(MvcResult r) throws Exception { return mapper.readValue(r.getResponse().getContentAsString(), FalhaEquipamentoResponseDTO.class); }
    private Map<String, Object> corpo(Long id) { var m = new HashMap<String, Object>(); m.put("ativoId", id); m.put("responsavelUsuarioId", responsavel); m.put("descricao", "Defeito"); m.put("criticidade", "ALTA"); return m; }
    private Usuario usuario(PerfilAcesso p) { return em.createQuery("select u from Usuario u where u.perfil=:perfil", Usuario.class).setParameter("perfil", p).getSingleResult(); }
    private Subestacao sub() { var s = new Subestacao(); s.setNome("Equipamento"); s.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(s); return s; }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", usuario(perfil).getEmail()).param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn(); csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
