package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.AlimentadorResponseDTO;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlimentadorControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private AlimentadorRepository alimentadorRepository;
	@Autowired private SubestacaoRepository subestacaoRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private MockHttpSession sessao;
	private CsrfResponse csrf;
	private Long subestacaoId;

	@BeforeEach
	void preparar() throws Exception {
		subestacaoId = salvarSubestacao();
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
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveCriarEConsultarComRelacionamento(EstadoOperacional estado) throws Exception {
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", estado);
		MvcResult result = enviar(post("/api/alimentadores"), request, 201);
		AlimentadorResponseDTO response = resposta(result);
		assertThat(result.getResponse().getHeader("Location"))
				.isEqualTo("http://localhost/api/alimentadores/" + response.id());
		assertThat(response.id()).isPositive();
		assertThat(response.nome()).isEqualTo("Alimentador Central");
		assertThat(response.subestacaoId()).isEqualTo(subestacaoId);
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		assertThat(response.latitude()).isEqualByComparingTo("-25.4284001");
		assertThat(response.longitude()).isEqualByComparingTo("-49.2733001");
		assertThat(response.tensaoNominalKv()).isEqualByComparingTo("13.812345");
		assertThat(response.dataInstalacao()).isEqualTo(LocalDate.of(2099, 1, 15));
		entityManager.flush();
		entityManager.clear();
		assertThat(alimentadorRepository.findById(response.id()).orElseThrow().getSubestacao().getId())
				.isEqualTo(subestacaoId);
		mockMvc.perform(get("/api/alimentadores/{id}", response.id()).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()))
				.andExpect(jsonPath("$.subestacao").doesNotExist())
				.andExpect(jsonPath("$.capacidadeMva").doesNotExist());
	}

	@Test
	void deveListarColecaoVaziaEPreenchida() throws Exception {
		mockMvc.perform(get("/api/alimentadores").session(sessao))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
		Long primeiro = criar();
		Long segundo = criar();
		MvcResult result = mockMvc.perform(get("/api/alimentadores").session(sessao))
				.andExpect(status().isOk()).andReturn();
		AlimentadorResponseDTO[] lista = objectMapper.readValue(
				result.getResponse().getContentAsString(), AlimentadorResponseDTO[].class);
		assertThat(lista).extracting(AlimentadorResponseDTO::id).containsExactlyInAnyOrder(primeiro, segundo);
	}

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveEditarDadosEVinculoPreservandoId(EstadoOperacional estado) throws Exception {
		Long id = criar();
		Long outraSubestacaoId = salvarSubestacao();
		Map<String, Object> request = requestValido();
		request.put("nome", "  Alimentador Atualizado  ");
		request.put("latitude", new BigDecimal("90.0000000"));
		request.put("longitude", new BigDecimal("180.0000000"));
		request.put("tensaoNominalKv", new BigDecimal("34.512345"));
		request.put("dataInstalacao", "2099-12-31");
		request.put("subestacaoId", outraSubestacaoId);
		request.put("estadoOperacional", estado);
		MvcResult result = enviar(put("/api/alimentadores/{id}", id), request, 200);
		AlimentadorResponseDTO response = resposta(result);
		assertThat(response.id()).isEqualTo(id);
		assertThat(response.nome()).isEqualTo("Alimentador Atualizado");
		assertThat(response.subestacaoId()).isEqualTo(outraSubestacaoId);
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/alimentadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
		assertThat(alimentadorRepository.count()).isEqualTo(1);
		assertThat(subestacaoRepository.count()).isEqualTo(2);
		assertThat(subestacaoRepository.findById(outraSubestacaoId).orElseThrow().getTensaoNominalKv())
				.isEqualByComparingTo("138");
	}

	@Test
	void deveAplicarEstadoPadraoQuandoOmitidoNoPostENoPut() throws Exception {
		AlimentadorResponseDTO criado = resposta(enviar(post("/api/alimentadores"), requestValido(), 201));
		assertThat(criado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", "FALHA");
		enviar(put("/api/alimentadores/{id}", criado.id()), request, 200);
		AlimentadorResponseDTO atualizado = resposta(enviar(
				put("/api/alimentadores/{id}", criado.id()), requestValido(), 200));
		assertThat(atualizado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
	}

	@Test
	void deveRetornar404ParaAlimentadorInexistente() throws Exception {
		mockMvc.perform(get("/api/alimentadores/{id}", Long.MAX_VALUE).session(sessao))
				.andExpect(status().isNotFound());
		enviar(put("/api/alimentadores/{id}", Long.MAX_VALUE), requestValido(), 404);
		assertThat(alimentadorRepository.count()).isZero();
	}

	@Test
	void deveRejeitarSubestacaoInexistenteSemAlterarDados() throws Exception {
		Map<String, Object> request = requestValido();
		request.put("subestacaoId", Long.MAX_VALUE);
		request.put("nome", "Não deve ser salvo");
		assertRejeitado(request, 404);
	}

	@ParameterizedTest
	@ValueSource(strings = {"nome", "latitude", "longitude", "tensaoNominalKv", "dataInstalacao", "subestacaoId"})
	void deveRejeitarCampoObrigatorioNulo(String campo) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, null);
		assertRejeitado(request, 400);
	}

	@ParameterizedTest
	@CsvSource({
			"tensaoNominalKv, 1.1234567", "tensaoNominalKv, 10000000000000",
			"tensaoNominalKv, 0", "tensaoNominalKv, -1",
			"latitude, 1.12345678", "longitude, 1.12345678",
			"latitude, -90.0000001", "latitude, 90.0000001",
			"longitude, -180.0000001", "longitude, 180.0000001",
			"subestacaoId, 0", "subestacaoId, -1"
	})
	void deveRejeitarValoresInvalidos(String campo, String valor) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, new BigDecimal(valor));
		assertRejeitado(request, 400);
	}

	@Test
	void deveRejeitarNomeInvalidoEEstadoDesconhecido() throws Exception {
		for (String nome : new String[] {"   ", "A".repeat(256)}) {
			Map<String, Object> request = requestValido();
			request.put("nome", nome);
			assertRejeitado(request, 400);
		}
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", "DESCONHECIDO");
		assertRejeitado(request, 400);
	}

	@Test
	void deveExigirAutenticacaoECsrf() throws Exception {
		Long id = criar();
		mockMvc.perform(get("/api/alimentadores")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/alimentadores/{id}", id)).andExpect(status().isUnauthorized());
		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				post("/api/alimentadores"), put("/api/alimentadores/{id}", id)}) {
			mockMvc.perform(request.session(sessao).contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestValido())))
					.andExpect(status().isForbidden());
		}
		assertThat(alimentadorRepository.count()).isEqualTo(1);
	}

	private void assertRejeitado(Map<String, Object> request, int statusEsperado) throws Exception {
		Long id = criar();
		entityManager.flush();
		entityManager.clear();
		String antes = mockMvc.perform(get("/api/alimentadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long total = alimentadorRepository.count();
		Long ativos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class);
		enviar(post("/api/alimentadores"), request, statusEsperado);
		enviar(put("/api/alimentadores/{id}", id), request, statusEsperado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/alimentadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(antes));
		assertThat(alimentadorRepository.count()).isEqualTo(total);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class)).isEqualTo(ativos);
	}

	private MvcResult enviar(MockHttpServletRequestBuilder request, Map<String, Object> dados, int esperado)
			throws Exception {
		return mockMvc.perform(request.session(sessao).header(csrf.headerName(), csrf.token())
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dados)))
				.andExpect(status().is(esperado)).andReturn();
	}

	private AlimentadorResponseDTO resposta(MvcResult result) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), AlimentadorResponseDTO.class);
	}

	private Long criar() throws Exception {
		return resposta(enviar(post("/api/alimentadores"), requestValido(), 201)).id();
	}

	private Long salvarSubestacao() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setTensaoNominalKv(new BigDecimal("138"));
		subestacao.setCapacidadeMva(new BigDecimal("100"));
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 1));
		subestacao.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		return subestacaoRepository.saveAndFlush(subestacao).getId();
	}

	private Map<String, Object> requestValido() {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("nome", "  Alimentador Central  ");
		request.put("latitude", new BigDecimal("-25.4284001"));
		request.put("longitude", new BigDecimal("-49.2733001"));
		request.put("tensaoNominalKv", new BigDecimal("13.812345"));
		request.put("dataInstalacao", "2099-01-15");
		request.put("subestacaoId", subestacaoId);
		return request;
	}

}
