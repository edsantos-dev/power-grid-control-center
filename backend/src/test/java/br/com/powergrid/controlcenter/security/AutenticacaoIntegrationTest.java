package br.com.powergrid.controlcenter.security;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "FRONTEND_URL=http://localhost:5173")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AutenticacaoIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cadastrarUsuario() {
		Usuario usuario = new Usuario();
		usuario.setNome("Operador de teste");
		usuario.setMatricula("AUTH-001");
		usuario.setEmail("operador@example.com");
		usuario.setPerfil(PerfilAcesso.OPERADOR);
		usuario.setSenhaHash(passwordEncoder.encode("SenhaTeste123!"));
		usuarioRepository.saveAndFlush(usuario);
	}

	@Test
	void deveAutenticarRenovarSessaoEConsultarDtoSeguro() throws Exception {
		SessaoCsrf csrf = obterCsrf(null);
		String idAnterior = csrf.sessao().getId();

		mockMvc.perform(login(csrf, "  OPERADOR@Example.COM  ", "SenhaTeste123!"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.email").value("operador@example.com"))
				.andExpect(jsonPath("$.perfil").value("OPERADOR"))
				.andExpect(jsonPath("$.senhaHash").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		assertThat(csrf.sessao().getId()).isNotEqualTo(idAnterior);
		mockMvc.perform(get("/api/auth/me").session(csrf.sessao()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nome").value("Operador de teste"))
				.andExpect(jsonPath("$.email").value("operador@example.com"))
				.andExpect(jsonPath("$.perfil").value("OPERADOR"))
				.andExpect(jsonPath("$.senhaHash").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@ParameterizedTest
	@CsvSource({
			"operador@example.com, senha-incorreta",
			"inexistente@example.com, SenhaTeste123!"
	})
	void deveRejeitarCredenciaisInvalidas(String email, String senha) throws Exception {
		SessaoCsrf csrf = obterCsrf(null);

		mockMvc.perform(login(csrf, email, senha)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me").session(csrf.sessao()))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/auth/me", "/api/subestacoes"})
	void deveBloquearRotasPrivadasSemSessao(String caminho) throws Exception {
		mockMvc.perform(get(caminho)).andExpect(status().isUnauthorized());
	}

	@Test
	void deveBloquearLoginSemCsrf() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("email", "operador@example.com")
				.param("password", "SenhaTeste123!"))
				.andExpect(status().isForbidden());
	}

	@Test
	void deveBloquearLoginComCsrfInvalido() throws Exception {
		SessaoCsrf csrf = obterCsrf(null);
		mockMvc.perform(post("/api/auth/login").session(csrf.sessao())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("email", "operador@example.com")
				.param("password", "SenhaTeste123!")
				.header(csrf.token().headerName(), "token-invalido"))
				.andExpect(status().isForbidden());
	}

	@Test
	void deveRenovarCsrfAposLoginEInvalidarSessaoNoLogout() throws Exception {
		SessaoCsrf anterior = obterCsrf(null);
		mockMvc.perform(login(anterior, "operador@example.com", "SenhaTeste123!"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/logout").session(anterior.sessao())
				.header(anterior.token().headerName(), anterior.token().token()))
				.andExpect(status().isForbidden());

		SessaoCsrf atual = obterCsrf(anterior.sessao());
		mockMvc.perform(post("/api/auth/logout").session(atual.sessao())
				.header(atual.token().headerName(), atual.token().token()))
				.andExpect(status().isNoContent());

		assertThat(atual.sessao().isInvalid()).isTrue();
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void deveBloquearLogoutSemCsrfEManterSessao() throws Exception {
		SessaoCsrf csrf = obterCsrf(null);
		mockMvc.perform(login(csrf, "operador@example.com", "SenhaTeste123!"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/logout").session(csrf.sessao()))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/auth/me").session(csrf.sessao())).andExpect(status().isOk());
	}

	@Test
	void devePermitirPreflightDoFrontendComCredenciais() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void deveRejeitarOrigemNaoPermitida() throws Exception {
		mockMvc.perform(get("/api/auth/csrf").header("Origin", "http://origem-invalida.example"))
				.andExpect(status().isForbidden());
	}

	private SessaoCsrf obterCsrf(MockHttpSession sessao) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/auth/csrf");
		if (sessao != null) {
			request.session(sessao);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		CsrfResponse token = objectMapper.readValue(result.getResponse().getContentAsString(), CsrfResponse.class);
		assertThat(token.token()).isNotBlank();
		return new SessaoCsrf((MockHttpSession) result.getRequest().getSession(false), token);
	}

	private MockHttpServletRequestBuilder login(SessaoCsrf csrf, String email, String senha) {
		return post("/api/auth/login").session(csrf.sessao())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(csrf.token().headerName(), csrf.token().token())
				.param("email", email).param("password", senha);
	}

	private record SessaoCsrf(MockHttpSession sessao, CsrfResponse token) {
	}

}
