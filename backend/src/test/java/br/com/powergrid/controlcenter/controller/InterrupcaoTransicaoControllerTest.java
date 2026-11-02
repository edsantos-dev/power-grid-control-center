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
class InterrupcaoTransicaoControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00.123456Z");
    private static final Instant INICIO = AGORA.minusSeconds(3600);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EventoOperacionalRepository eventos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Long eventoId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var ativo = new Subestacao(); ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(ativo); em.flush();
        autenticar(PerfilAcesso.OPERADOR);
        var corpo = Map.of("ativoId", ativo.getId(), "responsavelUsuarioId", usuario(PerfilAcesso.OPERADOR),
                "descricao", "Falta de energia", "inicioEfetivo", INICIO.toString());
        var result = mvc.perform(post("/api/interrupcoes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo))).andExpect(status().isCreated()).andReturn();
        eventoId = mapper.readValue(result.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class).id();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "nulo", "anterior", "futuro", "semFuso", "invalido"})
    void deveRejeitarTerminoFisicoAusenteOuInvalidoSemAlterarEstadoOuAuditoria(String caso) throws Exception {
        iniciar(); var corpo = conclusao();
        switch (caso) {
            case "nulo" -> corpo.put("terminoEfetivo", null);
            case "anterior" -> corpo.put("terminoEfetivo", INICIO.minusNanos(1).toString());
            case "futuro" -> corpo.put("terminoEfetivo", AGORA.plusNanos(1).toString());
            case "semFuso" -> corpo.put("terminoEfetivo", "2026-11-01T11:30:00");
            case "invalido" -> corpo.put("terminoEfetivo", "invalido");
            default -> { }
        }
        enviar(corpo, 400); inalterado("EM_ANDAMENTO", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"historico", "igualInicio", "agora"})
    void deveConcluirPreservandoPeriodoFisicoComAuditoriaNoHorarioDoServidor(String caso) throws Exception {
        iniciar();
        Instant termino = caso.equals("igualInicio") ? INICIO : caso.equals("agora") ? AGORA : AGORA.minusSeconds(1800);
        var corpo = conclusao(); corpo.put("terminoEfetivo", termino.atOffset(ZoneOffset.ofHours(-3)).toString());
        corpo.put("usuarioId", usuario(PerfilAcesso.GERENTE_OPERACOES)); corpo.put("alteradoEm", INICIO.toString());
        var result = enviar(corpo, 201);
        var historico = mapper.readValue(result.getResponse().getContentAsString(), br.com.powergrid.controlcenter.dto.HistoricoEventoResponseDTO.class);
        assertThat(historico.alteradoEm().toInstant()).isEqualTo(AGORA);
        assertThat(historico.usuarioId()).isEqualTo(usuario(PerfilAcesso.OPERADOR));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost" + base() + "/" + historico.id());
        em.clear(); var salvo = eventos.findById(eventoId).orElseThrow();
        assertThat(salvo.getSituacao()).isEqualTo("CONCLUIDO");
        assertThat(salvo.getInicioEfetivo().toInstant()).isEqualTo(INICIO);
        assertThat(salvo.getTerminoEfetivo().toInstant()).isEqualTo(termino);
        assertThat(salvo.getTerminoEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(em.createQuery("select count(h) from HistoricoEventoOperacional h where h.evento.id = :id", Long.class)
                .setParameter("id", eventoId).getSingleResult()).isEqualTo(2);
        mvc.perform(get("/api/eventos-operacionais/{id}", eventoId).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUTENCAO", "MANOBRA", "DESLIGAMENTO_PROGRAMADO", "SOBRECARGA", "FALHA_EQUIPAMENTO"})
    void deveManterTerminoAutomaticoNasDemaisCategorias(String categoria) throws Exception {
        var evento = eventos.findById(eventoId).orElseThrow();
        evento.setCategoria(EventoOperacional.Categoria.valueOf(categoria)); em.flush();
        iniciar(); enviar(conclusao(), 201); em.clear();
        assertThat(eventos.findById(eventoId).orElseThrow().getTerminoEfetivo().toInstant()).isEqualTo(AGORA);
    }

    @Test
    void deveRejeitarTerminoManualForaDaConclusaoDeInterrupcao() throws Exception {
        var iniciar = inicio(); iniciar.put("terminoEfetivo", AGORA.toString()); enviar(iniciar, 400);
        inalterado("ABERTO", 0);
    }

    @Test
    void deveRejeitarTerminoManualParaOutraCategoria() throws Exception {
        var evento = eventos.findById(eventoId).orElseThrow(); evento.setCategoria(EventoOperacional.Categoria.MANUTENCAO); em.flush();
        iniciar(); var corpo = conclusao(); corpo.put("terminoEfetivo", AGORA.toString()); enviar(corpo, 400);
        inalterado("EM_ANDAMENTO", 1);
    }

    @Test
    void devePreservarAutorizacaoDeResponsavelEGerente() throws Exception {
        iniciar(); autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        var corpo = conclusao(); corpo.put("terminoEfetivo", AGORA.minusSeconds(1800).toString());
        enviar(corpo, 403); inalterado("EM_ANDAMENTO", 1);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        var resultado = enviar(corpo, 201);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), br.com.powergrid.controlcenter.dto.HistoricoEventoResponseDTO.class);
        assertThat(dto.usuarioId()).isEqualTo(usuario(PerfilAcesso.GERENTE_OPERACOES));
    }

    @Test
    void devePreservarSituacaoEsperadaESaltosProibidos() throws Exception {
        var corpo = conclusao(); corpo.put("terminoEfetivo", AGORA.toString()); enviar(corpo, 409);
        corpo.put("situacaoEsperada", "ABERTO"); enviar(corpo, 409);
        inalterado("ABERTO", 0);
    }

    private void iniciar() throws Exception { enviar(inicio(), 201); }

    private Map<String, Object> inicio() {
        return new HashMap<>(Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Atendimento iniciado"));
    }

    private Map<String, Object> conclusao() {
        return new HashMap<>(Map.of("situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO", "comentario", "Fornecimento restabelecido"));
    }

    private org.springframework.test.web.servlet.MvcResult enviar(Map<String, Object> corpo, int esperado) throws Exception {
        return mvc.perform(post(base()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private void inalterado(String situacao, long transicoes) {
        em.clear(); var evento = eventos.findById(eventoId).orElseThrow();
        assertThat(evento.getSituacao()).isEqualTo(situacao);
        assertThat(evento.getTerminoEfetivo()).isNull();
        assertThat(evento.getInicioEfetivo().toInstant()).isEqualTo(INICIO);
        assertThat(em.createQuery("select count(h) from HistoricoEventoOperacional h where h.evento.id = :id", Long.class)
                .setParameter("id", eventoId).getSingleResult()).isEqualTo(transicoes);
    }

    private String base() { return "/api/eventos-operacionais/" + eventoId + "/transicoes"; }
    private Long usuario(PerfilAcesso perfil) {
        return usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow().getId();
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
