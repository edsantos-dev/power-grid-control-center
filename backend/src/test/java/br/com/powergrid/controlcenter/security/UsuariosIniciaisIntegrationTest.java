package br.com.powergrid.controlcenter.security;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.UsuarioAutenticadoResponse;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(UsuariosIniciaisIntegrationTest.GestaoAcessoTesteController.class)
class UsuariosIniciaisIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@ParameterizedTest
	@CsvSource({
			"operador@powergrid.local, OPERADOR, 403",
			"gerente@powergrid.local, GERENTE_OPERACOES, 200",
			"tecnico@powergrid.local, TECNICO_MANUTENCAO, 403"
	})
	void deveAutenticarContaDaV2EAplicarPermissaoPorMetodo(
			String email, PerfilAcesso perfil, int statusGestao) throws Exception {
		MvcResult respostaCsrf = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk()).andReturn();
		CsrfResponse csrf = objectMapper.readValue(
				respostaCsrf.getResponse().getContentAsString(), CsrfResponse.class);
		MockHttpSession sessao = (MockHttpSession) respostaCsrf.getRequest().getSession(false);

		MvcResult login = mockMvc.perform(post("/api/auth/login").session(sessao)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(csrf.headerName(), csrf.token())
				.param("email", email).param("password", "SenhaSegura123"))
				.andExpect(status().isOk()).andReturn();
		assertUsuarioSeguro(login.getResponse().getContentAsString(), email, perfil);

		MvcResult usuario = mockMvc.perform(get("/api/auth/me").session(sessao))
				.andExpect(status().isOk()).andReturn();
		assertUsuarioSeguro(usuario.getResponse().getContentAsString(), email, perfil);
		mockMvc.perform(get("/teste/gestao-acesso").session(sessao))
				.andExpect(status().is(statusGestao));
	}

	@Test
	void deveRejeitarGestaoSemAutenticacao() throws Exception {
		mockMvc.perform(get("/teste/gestao-acesso")).andExpect(status().isUnauthorized());
	}

	private void assertUsuarioSeguro(String json, String email, PerfilAcesso perfil) {
		assertThat(objectMapper.readTree(json).properties()).hasSize(4);
		UsuarioAutenticadoResponse usuario = objectMapper.readValue(json, UsuarioAutenticadoResponse.class);
		assertThat(usuario.id()).isPositive();
		assertThat(usuario.nome()).isNotBlank();
		assertThat(usuario.email()).isEqualTo(email);
		assertThat(usuario.perfil()).isEqualTo(perfil);
		assertThat(json).doesNotContain("senhaHash", "password", "SenhaSegura123", "$2a$");
	}

	@TestConfiguration(proxyBeanMethods = false)
	@RestController
	static class GestaoAcessoTesteController {
		@GetMapping("/teste/gestao-acesso")
		@PreAuthorize("hasRole('GERENTE_OPERACOES')")
		public String consultar() {
			return "permitido";
		}
	}

}
