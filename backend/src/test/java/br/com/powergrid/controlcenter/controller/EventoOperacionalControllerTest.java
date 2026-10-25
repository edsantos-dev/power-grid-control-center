package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.TransformadorRepository;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class EventoOperacionalControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private TransformadorRepository transformadorRepository;
	@Autowired private AlimentadorRepository alimentadorRepository;
	@Autowired private SubestacaoRepository subestacaoRepository;
	@Autowired private EntityManager entityManager;


	@Autowired private EventoOperacionalRepository repository;
	@Autowired private UsuarioRepository usuarioRepository;

	private MockHttpSession sessao;
	private CsrfResponse csrf;
	private Long alimentadorId;
	private Long subestacaoId;

	@BeforeEach
	void preparar() throws Exception {
		alimentadorId = salvarAlimentador();
		subestacaoId = alimentadorRepository.findById(alimentadorId).orElseThrow().getSubestacao().getId();
		MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		sessao = (MockHttpSession) inicial.getRequest().getSession(false);
		CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
		mockMvc.perform(post("/api/auth/login").session(sessao)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(token.headerName(), token.token())
				.param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
				.andExpect(status().isOk());
		MvcResult renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
		csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
	}


    @ParameterizedTest
    @ValueSource(strings = {"MANOBRA", "DESLIGAMENTO_PROGRAMADO"})
    void deveCriarConsultarEListarComAutoriaDaSessao(String categoria) throws Exception {
        mockMvc.perform(get("/api/eventos-operacionais").session(sessao))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        Map<String, Object> request = requestValido();
        request.put("categoria", categoria);
        MvcResult result = enviar(request, 201);
        EventoOperacionalResponseDTO response = resposta(result);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("http://localhost/api/eventos-operacionais/" + response.id());
        assertThat(response.ativoId()).isEqualTo(alimentadorId);
        assertThat(response.registradorUsuarioId()).isEqualTo(usuarioId("operador"));
        assertThat(response.responsavelUsuarioId()).isEqualTo(usuarioId("gerente"));
        assertThat(response.situacao()).isEqualTo("ABERTO");
        assertThat(response.descricao()).isEqualTo("Manobra planejada");
        assertThat(response.inicioEfetivo()).isNull();
        assertThat(response.terminoEfetivo()).isNull();
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/eventos-operacionais/{id}", response.id()).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.registradorUsuario").doesNotExist());
        mockMvc.perform(get("/api/eventos-operacionais").session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(response.id()));
    }

    @Test
    void naoDeveConfiarEmAutoriaOuEstadoEnviadosPeloCliente() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("registradorUsuarioId", usuarioId("gerente"));
        request.put("situacao", "CONCLUIDO");
        request.put("inicioEfetivo", "2026-01-01T10:00:00Z");
        request.put("terminoEfetivo", "2026-01-01T11:00:00Z");
        EventoOperacionalResponseDTO response = resposta(enviar(request, 201));
        entityManager.flush();
        entityManager.clear();
        var evento = repository.findById(response.id()).orElseThrow();
        assertThat(evento.getRegistradorUsuario().getId()).isEqualTo(usuarioId("operador"));
        assertThat(evento.getSituacao()).isEqualTo("ABERTO");
        assertThat(evento.getInicioEfetivo()).isNull();
        assertThat(evento.getTerminoEfetivo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "categoria", "descricao", "responsavelUsuarioId", "inicioPrevisto"})
    void deveRejeitarObrigatorioNulo(String campo) throws Exception {
        Map<String, Object> request = requestValido();
        request.put(campo, null);
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ativoId,0", "ativoId,-1", "responsavelUsuarioId,0", "responsavelUsuarioId,-1"})
    void deveRejeitarIdNaoPositivo(String campo, Long valor) throws Exception {
        Map<String, Object> request = requestValido();
        request.put(campo, valor);
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERRUPCAO_FORNECIMENTO", "SOBRECARGA", "FALHA_EQUIPAMENTO", "MANUTENCAO", "INVALIDA"})
    void deveRejeitarCategoriaForaDoRecorte(String categoria) throws Exception {
        Map<String, Object> request = requestValido();
        request.put("categoria", categoria);
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "responsavelUsuarioId"})
    void deveRetornar404ParaReferenciaInexistente(String campo) throws Exception {
        Map<String, Object> request = requestValido();
        request.put(campo, Long.MAX_VALUE);
        enviar(request, 404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRetornar404ParaEventoInexistente() throws Exception {
        mockMvc.perform(get("/api/eventos-operacionais/{id}", Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRejeitarDescricaoVaziaECriticidadeLonga() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("descricao", "   ");
        enviar(request, 400);
        request = requestValido();
        request.put("criticidade", "A".repeat(33));
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T12:59:59Z", "2026-01-01T12:00:00", "data-invalida"})
    void deveRejeitarTerminoAnteriorOuDataInvalida(String termino) throws Exception {
        Map<String, Object> request = requestValido();
        request.put("terminoPrevisto", termino);
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T13:00:00Z", "2026-01-01T14:00:00Z"})
    void deveAceitarIgualdadeOuTerminoPosteriorComFusosDistintos(String termino) throws Exception {
        Map<String, Object> request = requestValido();
        request.put("terminoPrevisto", termino);
        request.put("responsavelUsuarioId", usuarioId("operador"));
        EventoOperacionalResponseDTO response = resposta(enviar(request, 201));
        assertThat(response.registradorUsuarioId()).isEqualTo(response.responsavelUsuarioId());
        assertThat(response.terminoPrevisto().toInstant())
                .isEqualTo(java.time.OffsetDateTime.parse(termino).toInstant());
    }

    @Test
    void deveAceitarSubestacaoETransformadorComoAtivos() throws Exception {
        var transformador = new br.com.powergrid.controlcenter.model.Transformador();
        transformador.setNome("Transformador");
        transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setCapacidadeMva(new BigDecimal("5"));
        transformador.setAlimentador(alimentadorRepository.findById(alimentadorId).orElseThrow());
        transformadorRepository.saveAndFlush(transformador);
        for (Long id : new Long[] {subestacaoId, transformador.getId()}) {
            Map<String, Object> request = requestValido();
            request.put("ativoId", id);
            assertThat(resposta(enviar(request, 201)).ativoId()).isEqualTo(id);
        }
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        mockMvc.perform(get("/api/eventos-operacionais")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/eventos-operacionais/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/eventos-operacionais").session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/eventos-operacionais").session(sessao)
                        .header(csrf.headerName(), "invalido").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/eventos-operacionais")).andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarCriacaoAnonimaMesmoComCsrfValido() throws Exception {
        MvcResult inicial = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/eventos-operacionais").session(anonima)
                        .header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    private Long usuarioId(String nome) {
        return usuarioRepository.findByEmail(nome + "@powergrid.local").orElseThrow().getId();
    }

    private MvcResult enviar(Map<String, Object> request, int esperado) throws Exception {
        return mockMvc.perform(post("/api/eventos-operacionais").session(sessao)
                        .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private EventoOperacionalResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
    }

    private Map<String, Object> requestValido() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("ativoId", alimentadorId);
        request.put("categoria", "MANOBRA");
        request.put("descricao", "  Manobra planejada  ");
        request.put("responsavelUsuarioId", usuarioId("gerente"));
        request.put("inicioPrevisto", "2026-01-01T10:00:00-03:00");
        return request;
    }
	private Long salvarAlimentador() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setTensaoNominalKv(new BigDecimal("138"));
		subestacao.setCapacidadeMva(new BigDecimal("100"));
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 1));
		subestacao.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		subestacaoRepository.saveAndFlush(subestacao);

		Alimentador alimentador = new Alimentador();
		alimentador.setNome("Alimentador Central");
		alimentador.setLatitude(new BigDecimal("-25.4284002"));
		alimentador.setLongitude(new BigDecimal("-49.2733002"));
		alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
		alimentador.setDataInstalacao(LocalDate.of(2099, 1, 2));
		alimentador.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		alimentador.setSubestacao(subestacao);
		return alimentadorRepository.saveAndFlush(alimentador).getId();
	}

}
