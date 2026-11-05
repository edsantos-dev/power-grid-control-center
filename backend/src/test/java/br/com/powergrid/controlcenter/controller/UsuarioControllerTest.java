package br.com.powergrid.controlcenter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UsuarioControllerTest {
    private static final String GERENTE = "gerente@powergrid.local";
    private static final String SENHA_VALIDA = "CadastroForte1!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @MockitoSpyBean private UsuarioRepository usuarios;

    private final String email = "cadastro-" + UUID.randomUUID() + "@example.test";

    @AfterEach
    void removerUsuarioCriado() {
        jdbc.update("DELETE FROM usuario WHERE email = ?", email);
    }

    @Test
    void cadastraComAuditoriaESenhaEmBcryptEPermiteLogin() throws Exception {
        var sessao = entrar(GERENTE, "SenhaSegura123");
        Long autor = usuarios.findByEmail(GERENTE).orElseThrow().getId();
        Instant antes = Instant.now().minusSeconds(1);
        // Doze ou mais caracteres e exatamente 72 bytes, com caracteres multibyte.
        String senha = "Aa1!" + "é".repeat(34);

        var resposta = cadastrar(sessao, "  " + email.toUpperCase(java.util.Locale.ROOT) + " ", senha, "OPERADOR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Usuário novo"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.perfil").value("OPERADOR"))
                .andReturn().getResponse();

        var persistido = usuarios.findByEmail(email).orElseThrow();
        var corpo = json.readTree(resposta.getContentAsString());
        assertThat(corpo.properties()).hasSize(7);
        assertThat(corpo.get("criadoPorUsuarioId").asLong()).isEqualTo(autor);
        assertThat(corpo.get("matricula").asString()).isEqualTo(persistido.getMatricula()).startsWith("USR-");
        assertThat(persistido.getCriadoPorUsuarioId()).isEqualTo(autor);
        assertThat(persistido.getCriadoEm()).isBetween(antes, Instant.now());
        assertThat(Instant.parse(corpo.get("criadoEm").asString())).isEqualTo(persistido.getCriadoEm());
        assertThat(persistido.getSenhaHash()).isNotEqualTo(senha);
        assertThat(encoder.matches(senha, persistido.getSenhaHash())).isTrue();
        assertThat(resposta.getContentAsString()).doesNotContain("senha", "hash", senha, persistido.getSenhaHash());
        entrar(email, senha);
        cadastrar(sessao, email, SENHA_VALIDA, "OPERADOR").andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operador@powergrid.local", "tecnico@powergrid.local"})
    void bloqueiaOutrosPerfis(String login) throws Exception {
        cadastrar(entrar(login, "SenhaSegura123"), email, SENHA_VALIDA, "OPERADOR")
                .andExpect(status().isForbidden());
        assertThat(usuarios.existsByEmail(email)).isFalse();
    }

    @Test
    void exigeSessaoAutenticadaETokenCsrf() throws Exception {
        cadastrar(csrf(null), email, SENHA_VALIDA, "OPERADOR").andExpect(status().isUnauthorized());
        var gerente = entrar(GERENTE, "SenhaSegura123");
        mvc.perform(post("/api/usuarios").session(gerente.sessao())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(email, SENHA_VALIDA, "OPERADOR")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "'CurtaA1!', '12 caracteres'",
            "'SenhaSemNumero!', 'um número'",
            "'senhasemmaiuscula1!', 'uma letra maiúscula'",
            "'SENHASEMMINUSCULA1!', 'uma minúscula'",
            "'SenhaSemEspecial1 ', 'um caractere especial'",
            "'   ', 'Informe a senha'"
    })
    void rejeitaSenhaInvalidaSemEcoarCredencial(String senha, String motivo) throws Exception {
        validarErroDeSenha(senha, motivo);
    }

    @Test
    void rejeitaSenhaQueUltrapassa72BytesMesmoComMenosDe72Caracteres() throws Exception {
        validarErroDeSenha("Aa1!" + "é".repeat(34) + "b", "72 bytes em UTF-8");
    }

    @Test
    void rejeitaPerfilDesconhecidoSemEcoarSenha() throws Exception {
        var resposta = cadastrar(entrar(GERENTE, "SenhaSegura123"), email, SENHA_VALIDA, "ADMIN")
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(resposta.getContentAsString()).doesNotContain(SENHA_VALIDA);
        assertThat(usuarios.existsByEmail(email)).isFalse();
    }

    @Test
    void concorrenciaDevolve409MesmoQuandoAmbasAsConsultasPreliminaresNaoEncontramEmail() throws Exception {
        var primeiro = entrar(GERENTE, "SenhaSegura123");
        var segundo = entrar(GERENTE, "SenhaSegura123");
        var ambosConsultaram = new CyclicBarrier(2);
        doAnswer(invocation -> {
            ambosConsultaram.await(15, TimeUnit.SECONDS);
            return false;
        }).when(usuarios).existsByEmail(email);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> cadastrar(primeiro, email, SENHA_VALIDA, "OPERADOR")
                    .andReturn().getResponse());
            var b = executor.submit(() -> cadastrar(segundo, email, SENHA_VALIDA, "TECNICO_MANUTENCAO")
                    .andReturn().getResponse());
            var respostaA = a.get(30, TimeUnit.SECONDS);
            var respostaB = b.get(30, TimeUnit.SECONDS);
            assertThat(new int[] {respostaA.getStatus(), respostaB.getStatus()}).containsExactlyInAnyOrder(201, 409);
            var conflito = respostaA.getStatus() == 409 ? respostaA : respostaB;
            assertThat(json.readTree(conflito.getContentAsString()).get("message").asString())
                    .isEqualTo("E-mail já cadastrado.");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, email)).isEqualTo(1);
    }

    private void validarErroDeSenha(String senha, String motivo) throws Exception {
        var resposta = cadastrar(entrar(GERENTE, "SenhaSegura123"), email, senha, "OPERADOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("senha"))
                .andReturn().getResponse();
        var erro = json.readTree(resposta.getContentAsString()).get("fieldErrors").get(0);
        assertThat(erro.toString()).contains(motivo);
        assertThat(resposta.getContentAsString()).doesNotContain(senha);
        assertThat(usuarios.existsByEmail(email)).isFalse();
    }

    private ResultActions cadastrar(Sessao sessao, String endereco, String senha, String perfil) throws Exception {
        return mvc.perform(post("/api/usuarios").session(sessao.sessao())
                .header(sessao.token().headerName(), sessao.token().token())
                .contentType(MediaType.APPLICATION_JSON).content(payload(endereco, senha, perfil)));
    }

    private String payload(String endereco, String senha, String perfil) throws Exception {
        return json.writeValueAsString(Map.of("nome", " Usuário novo ", "email", endereco, "senha", senha, "perfil", perfil));
    }

    private Sessao entrar(String login, String senha) throws Exception {
        var inicial = csrf(null);
        mvc.perform(post("/api/auth/login").session(inicial.sessao())
                        .header(inicial.token().headerName(), inicial.token().token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", login).param("password", senha))
                .andExpect(status().isOk());
        return csrf(inicial.sessao());
    }

    private Sessao csrf(MockHttpSession sessao) throws Exception {
        var request = get("/api/auth/csrf");
        if (sessao != null) {
            request.session(sessao);
        }
        var resultado = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Sessao((MockHttpSession) resultado.getRequest().getSession(false),
                json.readValue(resultado.getResponse().getContentAsString(), CsrfResponse.class));
    }

    private record Sessao(MockHttpSession sessao, CsrfResponse token) {
    }
}
