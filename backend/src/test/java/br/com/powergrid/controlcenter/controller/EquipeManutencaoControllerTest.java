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
class EquipeManutencaoControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EquipeManutencaoRepository equipes;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception { autenticar(PerfilAcesso.OPERADOR); }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "TECNICO_MANUTENCAO"})
    void deveCriarEquipeComSituacaoInicialFixaELocation(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var corpo = new HashMap<String, Object>(Map.of("nome", "  Equipe Norte  ", "responsabilidades", "Inspeção preventiva",
                "especialidades", "Transformadores", "situacao", "DISPONIVEL"));
        var resultado = mvc.perform(post("/api/equipes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Equipe Norte"))
                .andExpect(jsonPath("$.situacao").value("FORA_DE_SERVICO"))
                .andExpect(jsonPath("$.responsabilidades").value("Inspeção preventiva"))
                .andExpect(jsonPath("$.especialidades").value("Transformadores")).andReturn();
        var resposta = mapper.readValue(resultado.getResponse().getContentAsString(), EquipeResponseDTO.class);
        assertThat(resultado.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/equipes/" + resposta.id());
        em.flush(); em.clear();
        assertThat(equipes.findById(resposta.id()).orElseThrow().getSituacao()).isEqualTo(SituacaoEquipe.FORA_DE_SERVICO);
        mvc.perform(get("/api/equipes/{id}", resposta.id()).session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(resposta.id()));
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "TECNICO_MANUTENCAO"})
    void deveEditarSomenteCamposTextuaisSemAlterarSituacao(PerfilAcesso perfil) throws Exception {
        var equipe = equipe("Nome anterior", SituacaoEquipe.EM_ATENDIMENTO);
        autenticar(perfil);
        mvc.perform(put("/api/equipes/{id}", equipe.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "nome", "  Nome atualizado  ", "responsabilidades", "Nova responsabilidade", "especialidades", "Linhas",
                        "situacao", "DISPONIVEL", "id", Long.MAX_VALUE))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(equipe.getId()))
                .andExpect(jsonPath("$.nome").value("Nome atualizado"))
                .andExpect(jsonPath("$.situacao").value("EM_ATENDIMENTO"));
        em.flush(); em.clear();
        var salva = equipes.findById(equipe.getId()).orElseThrow();
        assertThat(salva.getSituacao()).isEqualTo(SituacaoEquipe.EM_ATENDIMENTO);
        assertThat(salva.getResponsabilidades()).isEqualTo("Nova responsabilidade");
        assertThat(salva.getEspecialidades()).isEqualTo("Linhas");
    }

    @Test
    void deveAceitarCamposOpcionaisNulosELimiteDeNome() throws Exception {
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        var result = mvc.perform(post("/api/equipes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("nome", "A".repeat(255)))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.responsabilidades").isEmpty())
                .andExpect(jsonPath("$.especialidades").isEmpty()).andReturn();
        var resposta = mapper.readValue(result.getResponse().getContentAsString(), EquipeResponseDTO.class);
        mvc.perform(put("/api/equipes/{id}", resposta.id()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Atualizada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responsabilidades").isEmpty());
    }

    @Test
    void deveImpedirOperadorDeCriarOuEditarMesmoComCsrfValido() throws Exception {
        var equipe = equipe("Original", SituacaoEquipe.FORA_DE_SERVICO);
        long total = equipes.count();
        mvc.perform(post("/api/equipes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Nova\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/equipes/{id}", equipe.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Alterada\"}"))
                .andExpect(status().isForbidden());
        em.clear();
        assertThat(equipes.count()).isEqualTo(total);
        assertThat(equipes.findById(equipe.getId()).orElseThrow().getNome()).isEqualTo("Original");
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultasParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        var equipe = equipe("Leitura", SituacaoEquipe.FORA_DE_SERVICO);
        autenticar(perfil);
        mvc.perform(get("/api/equipes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(equipe.getId()));
        mvc.perform(get("/api/equipes/{id}", equipe.getId()).session(sessao)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nome\":null}", "{\"nome\":\"\"}", "{\"nome\":\"   \"}"})
    void deveRejeitarNomeObrigatorioNoPostEPut(String corpo) throws Exception {
        var equipe = equipe("Original", SituacaoEquipe.FORA_DE_SERVICO);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(post("/api/equipes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/equipes/{id}", equipe.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarNomeAcimaDoLimiteNoPostEPut() throws Exception {
        var equipe = equipe("Original", SituacaoEquipe.FORA_DE_SERVICO);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        var corpo = mapper.writeValueAsString(Map.of("nome", "A".repeat(256)));
        mvc.perform(post("/api/equipes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/equipes/{id}", equipe.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar404ParaEquipeAusenteNoGetEPut() throws Exception {
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(get("/api/equipes/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(put("/api/equipes/{id}", Long.MAX_VALUE).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Equipe\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExigirSessaoECsrfNasEscritas() throws Exception {
        var equipe = equipe("Original", SituacaoEquipe.FORA_DE_SERVICO);
        mvc.perform(get("/api/equipes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/equipes/{id}", equipe.getId())).andExpect(status().isUnauthorized());
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(post("/api/equipes").session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Equipe\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/equipes/{id}", equipe.getId()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Equipe\"}")).andExpect(status().isForbidden());
    }

    @Test
    void devePaginarOrdenarPorIdAscECombinarFiltros() throws Exception {
        var primeira = equipe("Equipe NORTE", SituacaoEquipe.DISPONIVEL);
        var segunda = equipe("Equipe Norte reserva", SituacaoEquipe.DISPONIVEL);
        equipe("Equipe Norte parada", SituacaoEquipe.FORA_DE_SERVICO);
        equipe("Equipe Sul", SituacaoEquipe.DISPONIVEL);
        mvc.perform(get("/api/equipes").session(sessao).param("nome", "nOrTe").param("situacao", "DISPONIVEL").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(primeira.getId()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/api/equipes").session(sessao).param("nome", "norte").param("situacao", "DISPONIVEL")
                .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(segunda.getId()))
                .andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/equipes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/equipes").session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void deveRetornarPaginaVaziaSemCorrespondencias() throws Exception {
        mvc.perform(get("/api/equipes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        equipe("Norte", SituacaoEquipe.FORA_DE_SERVICO);
        mvc.perform(get("/api/equipes").session(sessao).param("nome", "Sul")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5",
            "sort=id,desc", "situacao=INVALIDA", "situacao=disponivel", "ativoId=1", "dataInicio=2026-01-01T00:00:00Z"})
    void deveRejeitarParametrosInvalidos(String filtro) throws Exception {
        var partes = filtro.split("=", 2);
        mvc.perform(get("/api/equipes").session(sessao).param(partes[0], partes[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "nome", "situacao", "sort"})
    void deveRejeitarParametrosVaziosOuRepetidos(String parametro) throws Exception {
        mvc.perform(get("/api/equipes").session(sessao).param(parametro, "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/equipes").session(sessao).param(parametro, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadorInvalido(String id) throws Exception {
        mvc.perform(get("/api/equipes/" + id).session(sessao)).andExpect(status().isBadRequest());
    }

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
