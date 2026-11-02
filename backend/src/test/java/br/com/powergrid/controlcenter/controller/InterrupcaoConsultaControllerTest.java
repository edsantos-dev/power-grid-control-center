package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class InterrupcaoConsultaControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EventoOperacionalRepository eventos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Subestacao ativo;
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-01-01T13:00:00Z");

    @BeforeEach
    void preparar() throws Exception {
        ativo = ativo("Principal"); autenticar(PerfilAcesso.OPERADOR);
    }

    @Test
    void deveRetornarDetalheSomenteDeInterrupcao() throws Exception {
        var interrupcao = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "CONCLUIDO", BASE);
        var outra = evento(ativo, "MANUTENCAO", "ABERTO", BASE);
        mvc.perform(get("/api/interrupcoes/{id}", interrupcao.getId()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(interrupcao.getId())).andExpect(jsonPath("$.categoria").value("INTERRUPCAO_FORNECIMENTO"))
                .andExpect(jsonPath("$.situacao").value("CONCLUIDO")).andExpect(jsonPath("$.inicioEfetivo").isNotEmpty())
                .andExpect(jsonPath("$.terminoEfetivo").isNotEmpty());
        mvc.perform(get("/api/interrupcoes/{id}", outra.getId()).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(get("/api/interrupcoes/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
    }

    @Test
    void devePaginarPorInicioFisicoDescComDesempatePorIdESemOutrasCategorias() throws Exception {
        var antigo = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "CONCLUIDO", BASE.minusSeconds(60));
        var a = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "ABERTO", BASE);
        var b = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", BASE);
        var legado = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "ABERTO", null);
        evento(ativo, "MANOBRA", "ABERTO", BASE.plusSeconds(60));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(b.getId()))
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(a.getId()));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(antigo.getId()));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("size", "1").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(legado.getId())).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/interrupcoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void deveCombinarAtivoSituacaoEPeriodoComInicioInclusivoFimExclusivo() throws Exception {
        var outra = ativo("Outro");
        evento(ativo, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", BASE.minusSeconds(1));
        var esperado = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", BASE);
        evento(ativo, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", BASE.plusSeconds(60));
        evento(ativo, "INTERRUPCAO_FORNECIMENTO", "ABERTO", BASE);
        evento(outra, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", BASE);
        evento(ativo, "MANUTENCAO", "EM_ANDAMENTO", BASE);
        evento(ativo, "INTERRUPCAO_FORNECIMENTO", "EM_ANDAMENTO", null);
        mvc.perform(get("/api/interrupcoes").session(sessao).param("ativoId", ativo.getId().toString())
                .param("situacao", "EM_ANDAMENTO").param("dataInicio", "2026-01-01T10:00:00-03:00").param("dataFim", BASE.plusSeconds(60).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(esperado.getId()));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("ativoId", outra.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("situacao", "ABERTO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("dataFim", BASE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/interrupcoes").session(sessao).param("dataInicio", BASE.plusSeconds(60).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deveRetornarPaginaVaziaSemDadosOuCorrespondencias() throws Exception {
        mvc.perform(get("/api/interrupcoes").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        evento(ativo, "INTERRUPCAO_FORNECIMENTO", "ABERTO", BASE);
        mvc.perform(get("/api/interrupcoes").session(sessao).param("ativoId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultasParaTodosOsPerfisAutenticados(PerfilAcesso perfil) throws Exception {
        var evento = evento(ativo, "INTERRUPCAO_FORNECIMENTO", "ABERTO", BASE); autenticar(perfil);
        mvc.perform(get("/api/interrupcoes").session(sessao)).andExpect(status().isOk());
        mvc.perform(get("/api/interrupcoes/{id}", evento.getId()).session(sessao)).andExpect(status().isOk());
    }

    @Test
    void deveExigirSessaoNasConsultas() throws Exception {
        mvc.perform(get("/api/interrupcoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/interrupcoes/1")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5", "sort=id,asc",
            "ativoId=0", "ativoId=-1", "ativoId=abc", "ativoId=9223372036854775808", "situacao=RESOLVIDO", "situacao=aberto",
            "dataInicio=invalida", "dataFim=2026-01-01T10:00:00", "categoria=MANUTENCAO"})
    void deveRejeitarParametrosInvalidos(String filtro) throws Exception {
        var partes = filtro.split("=", 2);
        mvc.perform(get("/api/interrupcoes").session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "ativoId", "situacao", "dataInicio", "dataFim"})
    void deveRejeitarParametrosVaziosOuRepetidos(String filtro) throws Exception {
        mvc.perform(get("/api/interrupcoes").session(sessao).param(filtro, "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/interrupcoes").session(sessao).param(filtro, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T13:00:00Z", "2026-01-01T12:00:00Z"})
    void deveRejeitarPeriodoIgualOuInvertido(String fim) throws Exception {
        mvc.perform(get("/api/interrupcoes").session(sessao).param("dataInicio", BASE.toString()).param("dataFim", fim))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadorDeDetalheInvalido(String id) throws Exception {
        mvc.perform(get("/api/interrupcoes/" + id).session(sessao)).andExpect(status().isBadRequest());
    }

    private Subestacao ativo(String nome) {
        var sub = new Subestacao(); sub.setNome(nome); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub); em.flush(); return sub;
    }

    private EventoOperacional evento(Subestacao ativo, String categoria, String situacao, OffsetDateTime inicio) {
        var usuario = usuarios.findAll().getFirst();
        var evento = new EventoOperacional(); evento.setAtivo(ativo); evento.setCategoria(EventoOperacional.Categoria.valueOf(categoria));
        evento.setDescricao("Ocorrência de teste"); evento.setSituacao(situacao); evento.setRegistradorUsuario(usuario); evento.setResponsavelUsuario(usuario);
        evento.setInicioEfetivo(inicio);
        if (situacao.equals("CONCLUIDO") && inicio != null) evento.setTerminoEfetivo(inicio.plusSeconds(60));
        return eventos.saveAndFlush(evento);
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario.getEmail()).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
