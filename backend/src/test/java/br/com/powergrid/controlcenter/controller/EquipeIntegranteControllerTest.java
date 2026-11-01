package br.com.powergrid.controlcenter.controller;

import java.util.HashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.EquipeResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.EquipeManutencaoRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class EquipeIntegranteControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EquipeManutencaoRepository equipes;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private java.time.Clock clock;
    private static final java.time.Instant AGORA = java.time.Instant.parse("2026-11-01T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(AGORA);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveExecutarCicloCompletoComIdempotenciaEHistoricoDeVigencia() throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        long usuario = usuario(PerfilAcesso.OPERADOR);
        var primeira = adicionar(equipe.getId(), usuario, 201);
        assertThat(primeira.inicioVigencia().toInstant()).isEqualTo(AGORA);
        assertThat(primeira.inicioVigencia().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        assertThat(adicionar(equipe.getId(), usuario, 200)).isEqualTo(primeira);
        situacao(equipe.getId(), "DISPONIVEL", 200);
        remover(equipe.getId(), usuario, 409);
        situacao(equipe.getId(), "FORA_DE_SERVICO", 200);
        remover(equipe.getId(), usuario, 204);
        em.flush(); em.clear();
        var encerrado = em.find(VinculoEquipe.class, primeira.id());
        assertThat(encerrado.getFimVigencia().toInstant()).isEqualTo(AGORA.plusSeconds(60));
        org.mockito.Mockito.when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
        remover(equipe.getId(), usuario, 204);
        em.clear();
        assertThat(em.find(VinculoEquipe.class, primeira.id()).getFimVigencia().toInstant()).isEqualTo(AGORA.plusSeconds(60));
        var nova = adicionar(equipe.getId(), usuario, 201);
        assertThat(nova.id()).isNotEqualTo(primeira.id());
        assertThat(nova.inicioVigencia().toInstant()).isEqualTo(AGORA.plusSeconds(120));
        mvc.perform(get(base(equipe.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(nova.id()))
                .andExpect(jsonPath("$.content[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].senha").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPONIVEL", "EM_ATENDIMENTO"})
    void deveExigirIntegranteAtivoEImpedirRemocaoDoUltimo(String estado) throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        situacao(equipe.getId(), estado, 409);
        long usuario = usuario(PerfilAcesso.OPERADOR);
        var vinculo = adicionar(equipe.getId(), usuario, 201);
        remover(equipe.getId(), usuario, 204);
        situacao(equipe.getId(), estado, 409);
        adicionar(equipe.getId(), usuario, 201);
        situacao(equipe.getId(), estado, 200);
        remover(equipe.getId(), usuario, 409);
        em.flush(); em.clear();
        assertThat(equipes.findById(equipe.getId()).orElseThrow().getSituacao().name()).isEqualTo(estado);
        assertThat(em.createQuery("select count(v) from VinculoEquipe v where v.equipe.id = :id and v.fimVigencia is null", Long.class)
                .setParameter("id", equipe.getId()).getSingleResult()).isEqualTo(1);
        assertThat(em.find(VinculoEquipe.class, vinculo.id()).getFimVigencia()).isNotNull();
    }

    @Test
    void devePermitirRemoverUmDeDoisIntegrantesSemTirarEquipeDeServico() throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        long a = usuario(PerfilAcesso.OPERADOR), b = usuario(PerfilAcesso.TECNICO_MANUTENCAO);
        adicionar(equipe.getId(), a, 201); adicionar(equipe.getId(), b, 201);
        situacao(equipe.getId(), "EM_ATENDIMENTO", 200);
        remover(equipe.getId(), a, 204); remover(equipe.getId(), b, 409);
        mvc.perform(get(base(equipe.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].usuarioId").value(b));
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void deveAceitarIntegrantesDeTodosOsPerfisEMultiplasEquipes(PerfilAcesso perfil) throws Exception {
        var a = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        var b = equipe("Sul", SituacaoEquipe.FORA_DE_SERVICO);
        long usuario = usuario(perfil);
        adicionar(a.getId(), usuario, 201); adicionar(b.getId(), usuario, 201);
        mvc.perform(get(base(a.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].perfil").value(perfil.name()));
        mvc.perform(get(base(b.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(usuario));
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "TECNICO_MANUTENCAO"})
    void devePermitirEscritasAosDoisPerfisGestores(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        long usuario = usuario(PerfilAcesso.OPERADOR);
        adicionar(equipe.getId(), usuario, 201);
        situacao(equipe.getId(), "DISPONIVEL", 200);
        situacao(equipe.getId(), "FORA_DE_SERVICO", 200);
        remover(equipe.getId(), usuario, 204);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirLeituraAutenticada(PerfilAcesso perfil) throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        adicionar(equipe.getId(), usuario(PerfilAcesso.OPERADOR), 201);
        autenticar(perfil);
        mvc.perform(get(base(equipe.getId())).session(sessao)).andExpect(status().isOk());
    }

    @Test
    void deveBloquearOperadorEExigirCsrfNasEscritas() throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        long usuario = usuario(PerfilAcesso.OPERADOR);
        autenticar(PerfilAcesso.OPERADOR);
        mvc.perform(put(base(equipe.getId()) + "/" + usuario).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
        remover(equipe.getId(), usuario, 403); situacao(equipe.getId(), "DISPONIVEL", 403);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(put(base(equipe.getId()) + "/" + usuario).session(sessao)).andExpect(status().isForbidden());
        mvc.perform(delete(base(equipe.getId()) + "/" + usuario).session(sessao)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/equipes/{id}/situacao", equipe.getId()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"situacao\":\"FORA_DE_SERVICO\"}")).andExpect(status().isForbidden());
        mvc.perform(get(base(equipe.getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar404ParaEquipeOuUsuarioInexistente() throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        long usuario = usuario(PerfilAcesso.OPERADOR);
        mvc.perform(put(base(Long.MAX_VALUE) + "/" + usuario).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound());
        remover(Long.MAX_VALUE, usuario, 404); situacao(Long.MAX_VALUE, "FORA_DE_SERVICO", 404);
        mvc.perform(get(base(Long.MAX_VALUE)).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(put(base(equipe.getId()) + "/" + Long.MAX_VALUE).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound());
        remover(equipe.getId(), Long.MAX_VALUE, 404);
        remover(equipe.getId(), usuario, 204);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"situacao\":null}", "{\"situacao\":\"INVALIDA\"}"})
    void deveRejeitarSituacaoInvalida(String corpo) throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        mvc.perform(patch("/api/equipes/{id}/situacao", equipe.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
    }

    @Test
    void devePaginarApenasVinculosAbertosEIsolarEquipes() throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        var outra = equipe("Sul", SituacaoEquipe.FORA_DE_SERVICO);
        var a = adicionar(equipe.getId(), usuario(PerfilAcesso.OPERADOR), 201);
        var b = adicionar(equipe.getId(), usuario(PerfilAcesso.TECNICO_MANUTENCAO), 201);
        adicionar(outra.getId(), usuario(PerfilAcesso.GERENTE_OPERACOES), 201);
        mvc.perform(get(base(equipe.getId())).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(b.id()))
                .andExpect(jsonPath("$.totalElements").value(2));
        remover(equipe.getId(), a.usuarioId(), 204);
        mvc.perform(get(base(equipe.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(b.id())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base(equipe.getId())).session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=101", "size=0", "page=abc", "sort=id,desc", "situacao=DISPONIVEL"})
    void deveRejeitarPaginacaoOuFiltroInvalido(String valor) throws Exception {
        var equipe = equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        var partes = valor.split("=", 2);
        mvc.perform(get(base(equipe.getId())).session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest());
    }

    @Test
    void devePermitirPreflightDeleteDoFrontend() throws Exception {
        mvc.perform(options("/api/equipes/1/integrantes/1").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "DELETE").header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private br.com.powergrid.controlcenter.dto.IntegranteResponseDTO adicionar(Long equipe, Long usuario, int status) throws Exception {
        var result = mvc.perform(put(base(equipe) + "/" + usuario).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().is(status)).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), br.com.powergrid.controlcenter.dto.IntegranteResponseDTO.class);
    }

    private void remover(Long equipe, Long usuario, int esperado) throws Exception {
        mvc.perform(delete(base(equipe) + "/" + usuario).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().is(esperado));
    }

    private void situacao(Long equipe, String situacao, int esperado) throws Exception {
        mvc.perform(patch("/api/equipes/{id}/situacao", equipe).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("situacao", situacao))))
                .andExpect(status().is(esperado));
    }

    private String base(Long equipe) { return "/api/equipes/" + equipe + "/integrantes"; }
    private Long usuario(PerfilAcesso perfil) { return usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow().getId(); }

    private EquipeManutencao equipe(String nome, SituacaoEquipe situacao) {
        var equipe = new EquipeManutencao(); equipe.setNome(nome); equipe.setSituacao(situacao);
        return equipes.saveAndFlush(equipe);
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
