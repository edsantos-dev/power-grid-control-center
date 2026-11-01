package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.UsinaResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.UsinaGeradoraRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UsinaGeradoraControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsinaGeradoraRepository usinas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Subestacao sub;

    @BeforeEach
    void preparar() throws Exception { sub = subestacao("Principal"); autenticar(PerfilAcesso.GERENTE_OPERACOES); }

    @ParameterizedTest
    @EnumSource(TipoGeracao.class)
    void deveCriarComLocationEstadoPadraoEAssociacaoPersistente(TipoGeracao tipo) throws Exception {
        var corpo = corpo(); corpo.put("nome", "  Usina Norte  "); corpo.put("tipoGeracao", tipo.name());
        var result = mvc.perform(post("/api/usinas").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Usina Norte"))
                .andExpect(jsonPath("$.tipoGeracao").value(tipo.name())).andExpect(jsonPath("$.estadoOperacional").value("OPERACIONAL"))
                .andExpect(jsonPath("$.subestacaoId").value(sub.getId())).andExpect(jsonPath("$.capacidadeMaximaMw").value(150.123456))
                .andExpect(jsonPath("$.dataInstalacao").isEmpty()).andReturn();
        var dto = mapper.readValue(result.getResponse().getContentAsString(), UsinaResponseDTO.class);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/usinas/" + dto.id());
        em.flush(); em.clear();
        var salva = usinas.findById(dto.id()).orElseThrow();
        assertThat(salva.getSubestacao().getId()).isEqualTo(sub.getId());
        assertThat(salva.getTipoGeracao()).isEqualTo(tipo);
        assertThat(salva.getLatitude()).isEqualByComparingTo("-25.4284001");
        mvc.perform(get("/api/usinas/{id}", dto.id()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.id())).andExpect(jsonPath("$.latitude").value(-25.4284001));
    }

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void deveAceitarEstadoExplicitoDataFuturaELimitesGeograficos(EstadoOperacional estado) throws Exception {
        var corpo = corpo(); corpo.put("latitude", new BigDecimal("90.0000000")); corpo.put("longitude", new BigDecimal("-180.0000000"));
        corpo.put("dataInstalacao", "2099-01-01"); corpo.put("estadoOperacional", estado.name()); corpo.put("nome", "A".repeat(255));
        mvc.perform(post("/api/usinas").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoOperacional").value(estado.name())).andExpect(jsonPath("$.dataInstalacao").value("2099-01-01"));
    }

    @Test
    void deveEditarCadastroETrocarSubestacaoSemAlterarIdentidade() throws Exception {
        var usina = usina("Antiga", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        var outra = subestacao("Destino"); var corpo = corpo(); corpo.put("subestacaoId", outra.getId());
        corpo.put("tipoGeracao", "EOLICA"); corpo.put("capacidadeMaximaMw", new BigDecimal("200.123456"));
        corpo.put("estadoOperacional", "MANUTENCAO"); corpo.put("dataInstalacao", "2099-02-01");
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usina.getId()))
                .andExpect(jsonPath("$.subestacaoId").value(outra.getId())).andExpect(jsonPath("$.tipoGeracao").value("EOLICA"));
        em.flush(); em.clear();
        var salva = usinas.findById(usina.getId()).orElseThrow();
        assertThat(salva.getSubestacao().getId()).isEqualTo(outra.getId());
        assertThat(salva.getCapacidadeMaximaMw()).isEqualByComparingTo("200.123456");
        assertThat(salva.getEstadoOperacional()).isEqualTo(EstadoOperacional.MANUTENCAO);
        var semEstado = corpo(); semEstado.put("estadoOperacional", null);
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(semEstado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoOperacional").value("OPERACIONAL"))
                .andExpect(jsonPath("$.dataInstalacao").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveBloquearEscritasDosDemaisPerfisSemAlterarDados(PerfilAcesso perfil) throws Exception {
        var usina = usina("Original", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub); autenticar(perfil);
        long total = usinas.count(); var json = mapper.writeValueAsString(corpo());
        mvc.perform(post("/api/usinas").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        em.clear(); assertThat(usinas.count()).isEqualTo(total);
        assertThat(usinas.findById(usina.getId()).orElseThrow().getNome()).isEqualTo("Original");
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirLeituraParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        var usina = usina("Leitura", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub); autenticar(perfil);
        mvc.perform(get("/api/usinas").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/usinas/{id}", usina.getId()).session(sessao)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "latitude", "longitude", "tipoGeracao", "capacidadeMaximaMw", "subestacaoId"})
    void deveRejeitarCamposObrigatoriosNulosEmCriacaoEEdicao(String campo) throws Exception {
        var corpo = corpo(); corpo.put(campo, null); validar400(corpo);
    }

    @ParameterizedTest
    @CsvSource({"latitude,90.0000001", "latitude,-90.0000001", "longitude,180.0000001", "longitude,-180.0000001",
            "latitude,-25.12345678", "longitude,-49.12345678", "capacidadeMaximaMw,0", "capacidadeMaximaMw,-1",
            "capacidadeMaximaMw,1.1234567", "capacidadeMaximaMw,10000000000000", "subestacaoId,0", "subestacaoId,-1"})
    void deveRejeitarValoresForaDosLimitesOuPrecisaoSemArredondar(String campo, String valor) throws Exception {
        var corpo = corpo(); corpo.put(campo, new BigDecimal(valor)); validar400(corpo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "LONGO"})
    void deveRejeitarNomeInvalido(String nome) throws Exception {
        var corpo = corpo(); corpo.put("nome", nome.equals("LONGO") ? "A".repeat(256) : nome); validar400(corpo);
    }

    @ParameterizedTest
    @CsvSource({"tipoGeracao,INVALIDO", "estadoOperacional,PARADA", "dataInstalacao,invalida"})
    void deveRejeitarEnumsEDatasInvalidos(String campo, String valor) throws Exception {
        var corpo = corpo(); corpo.put(campo, valor); validar400(corpo);
    }

    @Test
    void deveRetornar404ParaSubestacaoAusenteSemModificarUsinaExistente() throws Exception {
        var usina = usina("Original", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        var corpo = corpo(); corpo.put("subestacaoId", Long.MAX_VALUE);
        var json = mapper.writeValueAsString(corpo);
        mvc.perform(post("/api/usinas").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        em.clear(); assertThat(usinas.count()).isEqualTo(1);
        assertThat(usinas.findById(usina.getId()).orElseThrow().getNome()).isEqualTo("Original");
    }

    @Test
    void deveRetornar404ParaUsinaInexistente() throws Exception {
        mvc.perform(get("/api/usinas/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(put("/api/usinas/{id}", Long.MAX_VALUE).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo()))).andExpect(status().isNotFound());
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        var usina = usina("Original", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        mvc.perform(get("/api/usinas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usinas/{id}", usina.getId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usinas").session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo()))).andExpect(status().isForbidden());
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo()))).andExpect(status().isForbidden());
    }

    @Test
    void deveCombinarFiltrosEPaginarEmOrdemFixa() throws Exception {
        var outra = subestacao("Outra");
        var a = usina("Parque NORTE", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        var b = usina("Parque norte 2", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        usina("Parque Norte parado", TipoGeracao.SOLAR, EstadoOperacional.MANUTENCAO, sub);
        usina("Parque Norte eólico", TipoGeracao.EOLICA, EstadoOperacional.OPERACIONAL, sub);
        usina("Parque Norte outra", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, outra);
        mvc.perform(get("/api/usinas").session(sessao).param("nome", "  nOrTe  ").param("tipoGeracao", "SOLAR")
                .param("estadoOperacional", "OPERACIONAL").param("subestacaoId", sub.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(a.getId()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/usinas").session(sessao).param("nome", "norte").param("tipoGeracao", "SOLAR")
                .param("estadoOperacional", "OPERACIONAL").param("subestacaoId", sub.getId().toString()).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(b.getId())).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/usinas").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/usinas").session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void deveRetornarPaginaVaziaSemCorrespondencias() throws Exception {
        mvc.perform(get("/api/usinas").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        usina("Norte", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        mvc.perform(get("/api/usinas").session(sessao).param("subestacaoId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void deveFiltrarCaracteresDeLikeComoTextoLiteral(String trecho) throws Exception {
        var alvo = usina("Usina " + trecho, TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        usina("Usina comum", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        mvc.perform(get("/api/usinas").session(sessao).param("nome", trecho)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(alvo.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "sort=id,desc",
            "tipoGeracao=solar", "estadoOperacional=PARADA", "subestacaoId=0", "subestacaoId=-1", "subestacaoId=abc",
            "subestacaoId=9223372036854775808", "dataInicio=2026-01-01T00:00:00Z"})
    void deveRejeitarParametrosInvalidos(String parametro) throws Exception {
        var partes = parametro.split("=", 2);
        mvc.perform(get("/api/usinas").session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "nome", "tipoGeracao", "estadoOperacional", "subestacaoId", "sort"})
    void deveRejeitarParametrosVaziosOuRepetidos(String parametro) throws Exception {
        mvc.perform(get("/api/usinas").session(sessao).param(parametro, "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/usinas").session(sessao).param(parametro, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadorInvalido(String id) throws Exception {
        mvc.perform(get("/api/usinas/" + id).session(sessao)).andExpect(status().isBadRequest());
    }

    private void validar400(Map<String, Object> corpo) throws Exception {
        var usina = usina("Original", TipoGeracao.SOLAR, EstadoOperacional.OPERACIONAL, sub);
        var json = mapper.writeValueAsString(corpo);
        mvc.perform(post("/api/usinas").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/usinas/{id}", usina.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
    }

    private Map<String, Object> corpo() {
        return new HashMap<>(Map.of("nome", "Usina Norte", "latitude", new BigDecimal("-25.4284001"),
                "longitude", new BigDecimal("-49.2733001"), "tipoGeracao", "SOLAR", "capacidadeMaximaMw", new BigDecimal("150.123456"),
                "subestacaoId", sub.getId()));
    }

    private UsinaGeradora usina(String nome, TipoGeracao tipo, EstadoOperacional estado, Subestacao subestacao) {
        var usina = new UsinaGeradora(); usina.setNome(nome); usina.setTipoGeracao(tipo); usina.setEstadoOperacional(estado);
        usina.setCapacidadeMaximaMw(new BigDecimal("150.123456")); usina.setSubestacao(subestacao);
        return usinas.saveAndFlush(usina);
    }

    private Subestacao subestacao(String nome) {
        var subestacao = new Subestacao(); subestacao.setNome(nome); subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(subestacao); em.flush(); return subestacao;
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
