package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.SubestacaoResponseDTO;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class SubestacaoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SubestacaoRepository subestacaoRepository;

	@Autowired
	private EntityManager entityManager;

	private MockHttpSession sessao;
	private CsrfResponse csrf;

	@BeforeEach
	void autenticar() throws Exception {
		MvcResult inicial = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk()).andReturn();
		sessao = (MockHttpSession) inicial.getRequest().getSession(false);
		CsrfResponse tokenInicial = objectMapper.readValue(
				inicial.getResponse().getContentAsString(), CsrfResponse.class);
		mockMvc.perform(post("/api/auth/login").session(sessao)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(tokenInicial.headerName(), tokenInicial.token())
				.param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
				.andExpect(status().isOk());
		MvcResult renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao))
				.andExpect(status().isOk()).andReturn();
		csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
	}

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveCriarSubestacaoComLocationEDadosPersistidos(EstadoOperacional estado) throws Exception {
		Map<String, Object> request = requestValido();
		if (estado != null) {
			request.put("estadoOperacional", estado.name());
		}
		MvcResult result = mockMvc.perform(post("/api/subestacoes").session(sessao)
				.header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.nome").value("Subestação Central"))
				.andReturn();
		SubestacaoResponseDTO response = objectMapper.readValue(
				result.getResponse().getContentAsString(), SubestacaoResponseDTO.class);
		assertThat(response.id()).isPositive();
		assertThat(result.getResponse().getHeader("Location"))
				.isEqualTo("http://localhost/api/subestacoes/" + response.id());
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		entityManager.flush();
		entityManager.clear();
		Subestacao salva = subestacaoRepository.findById(response.id()).orElseThrow();
		assertThat(salva.getNome()).isEqualTo(response.nome());
		assertThat(salva.getEstadoOperacional()).isEqualTo(response.estadoOperacional());
		assertThat(salva.getLatitude()).isEqualByComparingTo("-25.4284001");
		assertThat(salva.getLongitude()).isEqualByComparingTo("-49.2733001");
		assertThat(salva.getTensaoNominalKv()).isEqualByComparingTo("138.123456");
		assertThat(salva.getCapacidadeMva()).isEqualByComparingTo("100.654321");
		assertThat(salva.getDataInstalacao()).isEqualTo(LocalDate.of(2099, 1, 15));
	}

	@Test
	void deveUsarOperacionalQuandoEstadoForNulo() throws Exception {
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", null);
		mockMvc.perform(post("/api/subestacoes").session(sessao)
				.header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estadoOperacional").value("OPERACIONAL"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"nome", "latitude", "longitude", "tensaoNominalKv", "capacidadeMva", "dataInstalacao"})
	void deveRejeitarCampoObrigatorioNulo(String campo) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, null);
		assertRequisicaoInvalida(request);
	}

	@ParameterizedTest
	@CsvSource({
			"tensaoNominalKv, 1.1234567", "capacidadeMva, 1.1234567",
			"tensaoNominalKv, 10000000000000", "capacidadeMva, 10000000000000",
			"latitude, 1.12345678", "longitude, 1.12345678",
			"tensaoNominalKv, 0", "capacidadeMva, 0",
			"tensaoNominalKv, -1", "capacidadeMva, -1",
			"latitude, -90.0000001", "latitude, 90.0000001",
			"longitude, -180.0000001", "longitude, 180.0000001"
	})
	void deveRejeitarPrecisaoOuValorInvalidoSemPersistir(String campo, String valor) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, new BigDecimal(valor));
		assertRequisicaoInvalida(request);
	}

	@Test
	void deveRejeitarNomeEmBrancoOuLongo() throws Exception {
		Map<String, Object> request = requestValido();
		for (String nome : new String[] {"   ", "A".repeat(256)}) {
			request.put("nome", nome);
			assertRequisicaoInvalida(request);
		}
	}

	@Test
	void deveRejeitarEstadoDesconhecido() throws Exception {
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", "DESCONHECIDO");
		assertRequisicaoInvalida(request);
	}

	@Test
	void deveRejeitarCriacaoSemCsrf() throws Exception {
		long total = subestacaoRepository.count();
		mockMvc.perform(post("/api/subestacoes").session(sessao)
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestValido())))
				.andExpect(status().isForbidden());
		assertThat(subestacaoRepository.count()).isEqualTo(total);
	}

	@Test
	void deveRejeitarCriacaoSemAutenticacaoMesmoComCsrf() throws Exception {
		long total = subestacaoRepository.count();
		MvcResult anonimo = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		CsrfResponse token = objectMapper.readValue(anonimo.getResponse().getContentAsString(), CsrfResponse.class);
		mockMvc.perform(post("/api/subestacoes")
				.session((MockHttpSession) anonimo.getRequest().getSession(false))
				.header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestValido())))
				.andExpect(status().isUnauthorized());
		assertThat(subestacaoRepository.count()).isEqualTo(total);
	}

	private void assertRequisicaoInvalida(Map<String, Object> request) throws Exception {
		long total = subestacaoRepository.count();
		mockMvc.perform(post("/api/subestacoes").session(sessao)
				.header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Location"));
		assertThat(subestacaoRepository.count()).isEqualTo(total);
	}

	private Map<String, Object> requestValido() {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("nome", "  Subestação Central  ");
		request.put("latitude", new BigDecimal("-25.4284001"));
		request.put("longitude", new BigDecimal("-49.2733001"));
		request.put("tensaoNominalKv", new BigDecimal("138.123456"));
		request.put("capacidadeMva", new BigDecimal("100.654321"));
		request.put("dataInstalacao", "2099-01-15");
		return request;
	}

}
