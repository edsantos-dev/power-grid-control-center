package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.LinhaTransmissaoResponseDTO;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
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
class LinhaTransmissaoControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private LinhaTransmissaoRepository linhaTransmissaoRepository;
	@Autowired private SubestacaoRepository subestacaoRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private MockHttpSession sessao;
	private CsrfResponse csrf;
	private Long subestacaoOrigemId;
	private Long subestacaoDestinoId;

	@BeforeEach
	void preparar() throws Exception {
		subestacaoOrigemId = salvarSubestacao();
		subestacaoDestinoId = salvarSubestacao();
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
		MvcResult result = enviar(post("/api/linhas-transmissao"), request, 201);
		LinhaTransmissaoResponseDTO response = resposta(result);
		assertThat(result.getResponse().getHeader("Location"))
				.isEqualTo("http://localhost/api/linhas-transmissao/" + response.id());
		assertThat(response.id()).isPositive();
		assertThat(response.nome()).isEqualTo("LinhaTransmissao Central");
		assertThat(response.subestacaoOrigemId()).isEqualTo(subestacaoOrigemId);
		assertThat(response.subestacaoDestinoId()).isEqualTo(subestacaoDestinoId);
		assertThat(response.extensaoKm()).isEqualByComparingTo("123.123456");
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		assertThat(response.latitude()).isEqualByComparingTo("-25.4284001");
		assertThat(response.longitude()).isEqualByComparingTo("-49.2733001");
		assertThat(response.tensaoOperacaoKv()).isEqualByComparingTo("13.812345");
		assertThat(response.dataInstalacao()).isEqualTo(LocalDate.of(2099, 1, 15));
		entityManager.flush();
		entityManager.clear();
		assertThat(linhaTransmissaoRepository.findById(response.id()).orElseThrow().getSubestacaoOrigem().getId())
				.isEqualTo(subestacaoOrigemId);
		mockMvc.perform(get("/api/linhas-transmissao/{id}", response.id()).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()))
				.andExpect(jsonPath("$.subestacao").doesNotExist())
				.andExpect(jsonPath("$.capacidadeMva").doesNotExist());
	}

	@Test
	void deveListarColecaoVaziaEPreenchida() throws Exception {
		mockMvc.perform(get("/api/linhas-transmissao").session(sessao))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
		Long primeiro = criar();
		Long segundo = criar();
		MvcResult result = mockMvc.perform(get("/api/linhas-transmissao").session(sessao))
				.andExpect(status().isOk()).andReturn();
		LinhaTransmissaoResponseDTO[] lista = objectMapper.readValue(
				result.getResponse().getContentAsString(), LinhaTransmissaoResponseDTO[].class);
		assertThat(lista).extracting(LinhaTransmissaoResponseDTO::id).containsExactlyInAnyOrder(primeiro, segundo);
	}

	@ParameterizedTest
	@EnumSource(EstadoOperacional.class)
	@NullSource
	void deveEditarDadosEVinculoPreservandoId(EstadoOperacional estado) throws Exception {
		Long id = criar();
		Long outraSubestacaoId = salvarSubestacao();
		Map<String, Object> request = requestValido();
		request.put("nome", "  LinhaTransmissao Atualizado  ");
		request.put("latitude", new BigDecimal("90.0000000"));
		request.put("longitude", new BigDecimal("180.0000000"));
		request.put("tensaoOperacaoKv", new BigDecimal("34.512345"));
		request.put("dataInstalacao", "2099-12-31");
		request.put("subestacaoOrigemId", outraSubestacaoId);
		request.put("estadoOperacional", estado);
		MvcResult result = enviar(put("/api/linhas-transmissao/{id}", id), request, 200);
		LinhaTransmissaoResponseDTO response = resposta(result);
		assertThat(response.id()).isEqualTo(id);
		assertThat(response.nome()).isEqualTo("LinhaTransmissao Atualizado");
		assertThat(response.subestacaoOrigemId()).isEqualTo(outraSubestacaoId);
		assertThat(response.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/linhas-transmissao/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
		assertThat(linhaTransmissaoRepository.count()).isEqualTo(1);
		assertThat(subestacaoRepository.count()).isEqualTo(3);
		assertThat(subestacaoRepository.findById(outraSubestacaoId).orElseThrow().getTensaoNominalKv())
				.isEqualByComparingTo("138");
	}

	@Test
	void deveAplicarEstadoPadraoQuandoOmitidoNoPostENoPut() throws Exception {
		LinhaTransmissaoResponseDTO criado = resposta(enviar(post("/api/linhas-transmissao"), requestValido(), 201));
		assertThat(criado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
		Map<String, Object> request = requestValido();
		request.put("estadoOperacional", "FALHA");
		enviar(put("/api/linhas-transmissao/{id}", criado.id()), request, 200);
		LinhaTransmissaoResponseDTO atualizado = resposta(enviar(
				put("/api/linhas-transmissao/{id}", criado.id()), requestValido(), 200));
		assertThat(atualizado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
	}

	@Test
	void deveRetornar404ParaLinhaTransmissaoInexistente() throws Exception {
		mockMvc.perform(get("/api/linhas-transmissao/{id}", Long.MAX_VALUE).session(sessao))
				.andExpect(status().isNotFound());
		enviar(put("/api/linhas-transmissao/{id}", Long.MAX_VALUE), requestValido(), 404);
		assertThat(linhaTransmissaoRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"subestacaoOrigemId", "subestacaoDestinoId"})
	void deveRejeitarSubestacaoInexistenteSemAlterarDados(String campo) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, Long.MAX_VALUE);
		request.put("nome", "Não deve ser salvo");
		assertRejeitado(request, 404);
	}

	@ParameterizedTest
	@ValueSource(strings = {"nome", "extensaoKm", "tensaoOperacaoKv", "dataInstalacao", "subestacaoOrigemId", "subestacaoDestinoId"})
	void deveRejeitarCampoObrigatorioNulo(String campo) throws Exception {
		Map<String, Object> request = requestValido();
		request.put(campo, null);
		assertRejeitado(request, 400);
	}

	@ParameterizedTest
	@CsvSource({
			"extensaoKm, 1.1234567", "extensaoKm, 10000000000000",
			"extensaoKm, 0", "extensaoKm, -1",
			"subestacaoDestinoId, 0", "subestacaoDestinoId, -1",
			"tensaoOperacaoKv, 1.1234567", "tensaoOperacaoKv, 10000000000000",
			"tensaoOperacaoKv, 0", "tensaoOperacaoKv, -1",
			"latitude, 1.12345678", "longitude, 1.12345678",
			"latitude, -90.0000001", "latitude, 90.0000001",
			"longitude, -180.0000001", "longitude, 180.0000001",
			"subestacaoOrigemId, 0", "subestacaoOrigemId, -1"
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
		mockMvc.perform(get("/api/linhas-transmissao")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/linhas-transmissao/{id}", id)).andExpect(status().isUnauthorized());
		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				post("/api/linhas-transmissao"), put("/api/linhas-transmissao/{id}", id)}) {
			mockMvc.perform(request.session(sessao).contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestValido())))
					.andExpect(status().isForbidden());
		}
		assertThat(linhaTransmissaoRepository.count()).isEqualTo(1);
	}

	@Test
	void deveRejeitarExtremidadesIguais() throws Exception {
		Map<String, Object> request = requestValido();
		request.put("subestacaoDestinoId", subestacaoOrigemId);
		assertRejeitado(request, 400);
	}

	@Test
	void deveAceitarCoordenadasAusentesERejeitarParIncompleto() throws Exception {
		Map<String, Object> request = requestValido();
		request.remove("latitude");
		request.remove("longitude");
		Long id = resposta(enviar(post("/api/linhas-transmissao"), request, 201)).id();
		assertThat(resposta(enviar(put("/api/linhas-transmissao/{id}", id), request, 200)).latitude()).isNull();
		for (String campo : new String[] {"latitude", "longitude"}) {
			Map<String, Object> incompleto = requestValido();
			incompleto.remove(campo);
			assertRejeitado(incompleto, 400);
		}
	}

	private void assertRejeitado(Map<String, Object> request, int statusEsperado) throws Exception {
		Long id = criar();
		entityManager.flush();
		entityManager.clear();
		String antes = mockMvc.perform(get("/api/linhas-transmissao/{id}", id).session(sessao))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long total = linhaTransmissaoRepository.count();
		Long ativos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class);
		enviar(post("/api/linhas-transmissao"), request, statusEsperado);
		enviar(put("/api/linhas-transmissao/{id}", id), request, statusEsperado);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/linhas-transmissao/{id}", id).session(sessao))
				.andExpect(status().isOk()).andExpect(content().json(antes));
		assertThat(linhaTransmissaoRepository.count()).isEqualTo(total);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ativo_rede", Long.class)).isEqualTo(ativos);
	}

	private MvcResult enviar(MockHttpServletRequestBuilder request, Map<String, Object> dados, int esperado)
			throws Exception {
		return mockMvc.perform(request.session(sessao).header(csrf.headerName(), csrf.token())
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dados)))
				.andExpect(status().is(esperado)).andReturn();
	}

	private LinhaTransmissaoResponseDTO resposta(MvcResult result) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), LinhaTransmissaoResponseDTO.class);
	}

	private Long criar() throws Exception {
		return resposta(enviar(post("/api/linhas-transmissao"), requestValido(), 201)).id();
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
		request.put("nome", "  LinhaTransmissao Central  ");
		request.put("latitude", new BigDecimal("-25.4284001"));
		request.put("longitude", new BigDecimal("-49.2733001"));
		request.put("tensaoOperacaoKv", new BigDecimal("13.812345"));
		request.put("dataInstalacao", "2099-01-15");
		request.put("subestacaoOrigemId", subestacaoOrigemId);
		request.put("subestacaoDestinoId", subestacaoDestinoId);
		request.put("extensaoKm", new BigDecimal("123.123456"));
		return request;
	}

}
