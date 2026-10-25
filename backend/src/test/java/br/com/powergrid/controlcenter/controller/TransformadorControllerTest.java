package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.TransformadorResponseDTO;
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
class TransformadorControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private TransformadorRepository transformadorRepository;
	@Autowired private AlimentadorRepository alimentadorRepository;
	@Autowired private SubestacaoRepository subestacaoRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbcTemplate;

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
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveCriarEConsultarComRelacionamento(EstadoOperacional estado) throws Exception {
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", estado);
		MvcResult result = enviar(post("/api/transformadores"), request, 201);
		TransformadorResponseDTO response = resposta(result);
		assertThat(result.getResponse().getHeader("Location"))
				.isEqualTo("http://localhost/api/transformadores/" + response.id());
		assertThat(response.id()).isPositive();
		assertThat(response.nome()).isEqualTo("Transformador Central");
		assertThat(response.alimentadorId()).isEqualTo(alimentadorId);
		assertThat(response.subestacaoId()).isEqualTo(subestacaoId);
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		assertThat(response.latitude()).isEqualByComparingTo("-25.4284001");
		assertThat(response.longitude()).isEqualByComparingTo("-49.2733001");
		assertThat(response.capacidadeMva()).isEqualByComparingTo("13.812345");
		assertThat(response.dataInstalacao()).isEqualTo(LocalDate.of(2099, 1, 15));
		entityManager.flush();
		entityManager.clear();
		assertThat(transformadorRepository.findById(response.id()).orElseThrow().getAlimentador().getId())
				.isEqualTo(alimentadorId);
		mockMvc.perform(get("/api/transformadores/{id}", response.id()).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()))
				.andExpect(jsonPath("$.alimentador").doesNotExist())
				.andExpect(jsonPath("$.tensaoNominalKv").doesNotExist());
	}

	@Test
	void deveListarColecaoVaziaEPreenchida() throws Exception {
		mockMvc.perform(get("/api/transformadores").session(sessao))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
		Long primeiro = criar();
		Long segundo = criar();
		entityManager.flush();
		entityManager.clear();
		MvcResult result = mockMvc.perform(get("/api/transformadores").session(sessao))
				.andExpect(status().isOk()).andReturn();
		TransformadorResponseDTO[] lista = objectMapper.readValue(
				result.getResponse().getContentAsString(), TransformadorResponseDTO[].class);
		assertThat(lista).extracting(TransformadorResponseDTO::id).containsExactlyInAnyOrder(primeiro, segundo);
		assertThat(lista).extracting(TransformadorResponseDTO::subestacaoId).containsOnly(subestacaoId);
	}

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveEditarDadosEVinculoPreservandoId(EstadoOperacional estado) throws Exception {
		Long id = criar();
		Long outroAlimentadorId = salvarAlimentador();
		Map<String, Object> request = requestValido();
		request.put("nome", "  Transformador Atualizado  ");
		request.put("latitude", new BigDecimal("90.0000000"));
		request.put("longitude", new BigDecimal("180.0000000"));
		request.put("capacidadeMva", new BigDecimal("34.512345"));
		request.put("dataInstalacao", "2099-12-31");
		request.put("alimentadorId", outroAlimentadorId);
		request.put("estadoOperacional", estado);
		MvcResult result = enviar(put("/api/transformadores/{id}", id), request, 200);
		TransformadorResponseDTO response = resposta(result);
		assertThat(response.id()).isEqualTo(id);
		assertThat(response.nome()).isEqualTo("Transformador Atualizado");
		assertThat(response.alimentadorId()).isEqualTo(outroAlimentadorId);
		Long novaSubestacaoId = alimentadorRepository.findById(outroAlimentadorId)
				.orElseThrow().getSubestacao().getId();
		assertThat(response.subestacaoId()).isEqualTo(novaSubestacaoId).isNotEqualTo(subestacaoId);
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/transformadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
		assertThat(transformadorRepository.count()).isEqualTo(1);
		assertThat(alimentadorRepository.count()).isEqualTo(2);
		assertThat(alimentadorRepository.findById(outroAlimentadorId).orElseThrow().getTensaoNominalKv())
				.isEqualByComparingTo("13.8");
	}

	@Test
	void deveAplicarEstadoPadraoQuandoOmitidoNoPostENoPut() throws Exception {
		TransformadorResponseDTO criado = resposta(enviar(post("/api/transformadores"), requestValido(), 201));
		assertThat(criado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", "FALHA");
		enviar(put("/api/transformadores/{id}", criado.id()), request, 200);
		TransformadorResponseDTO atualizado = resposta(enviar(
				put("/api/transformadores/{id}", criado.id()), requestValido(), 200));
		assertThat(atualizado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
	}

	@Test
	void deveRetornar404ParaTransformadorInexistente() throws Exception {
		mockMvc.perform(get("/api/transformadores/{id}", Long.MAX_VALUE).session(sessao))
				.andExpect(status().isNotFound());
		enviar(put("/api/transformadores/{id}", Long.MAX_VALUE), requestValido(), 404);
		assertThat(transformadorRepository.count()).isZero();
	}

	@Test
	void deveRejeitarAlimentadorInexistenteSemAlterarDados() throws Exception {
		Map<String, Object> request = requestValido();
		request.put("alimentadorId", Long.MAX_VALUE);
		request.put("nome", "Não deve ser salvo");
		assertRejeitado(request, 404);
	}

	@ParameterizedTest
	@ValueSource(strings = {"nome", "latitude", "longitude", "capacidadeMva", "dataInstalacao", "alimentadorId"})
	void deveRejeitarCampoObrigatorioNulo(String campo) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, null);
		assertRejeitado(request, 400);
	}

	@ParameterizedTest
	@CsvSource({
			"capacidadeMva, 1.1234567", "capacidadeMva, 10000000000000",
			"capacidadeMva, 0", "capacidadeMva, -1",
			"latitude, 1.12345678", "longitude, 1.12345678",
			"latitude, -90.0000001", "latitude, 90.0000001",
			"longitude, -180.0000001", "longitude, 180.0000001",
			"alimentadorId, 0", "alimentadorId, -1"
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
		mockMvc.perform(get("/api/transformadores")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/transformadores/{id}", id)).andExpect(status().isUnauthorized());
		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				post("/api/transformadores"), put("/api/transformadores/{id}", id)}) {
			mockMvc.perform(request.session(sessao).contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestValido())))
					.andExpect(status().isForbidden());
		}
		assertThat(transformadorRepository.count()).isEqualTo(1);
	}

	private void assertRejeitado(Map<String, Object> request, int statusEsperado) throws Exception {
		Long id = criar();
		entityManager.flush();
		entityManager.clear();
		String antes = mockMvc.perform(get("/api/transformadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long total = transformadorRepository.count();
		Long ativos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class);
		enviar(post("/api/transformadores"), request, statusEsperado);
		enviar(put("/api/transformadores/{id}", id), request, statusEsperado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/transformadores/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(antes));
		assertThat(transformadorRepository.count()).isEqualTo(total);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class)).isEqualTo(ativos);
	}

	private MvcResult enviar(MockHttpServletRequestBuilder request, Map<String, Object> dados, int esperado)
			throws Exception {
		return mockMvc.perform(request.session(sessao).header(csrf.headerName(), csrf.token())
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dados)))
				.andExpect(status().is(esperado)).andReturn();
	}

	private TransformadorResponseDTO resposta(MvcResult result) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), TransformadorResponseDTO.class);
	}

	private Long criar() throws Exception {
		return resposta(enviar(post("/api/transformadores"), requestValido(), 201)).id();
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

	private Map<String, Object> requestValido() {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("nome", "  Transformador Central  ");
		request.put("latitude", new BigDecimal("-25.4284001"));
		request.put("longitude", new BigDecimal("-49.2733001"));
		request.put("capacidadeMva", new BigDecimal("13.812345"));
		request.put("dataInstalacao", "2099-01-15");
		request.put("alimentadorId", alimentadorId);
		return request;
	}

}
