package br.com.powergrid.controlcenter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.repository.ConsumidorRepository;
import br.com.powergrid.controlcenter.repository.MedidorInteligenteRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Cadastros da rede (subestações, linhas, alimentadores, transformadores, medidores e consumidores):
 * gerente e operador escrevem; o técnico de manutenção apenas consulta.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class CadastrosRedePerfilControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SubestacaoRepository subestacoes;
    @Autowired private ConsumidorRepository consumidores;
    @Autowired private MedidorInteligenteRepository medidores;

    private MockHttpSession sessao;
    private CsrfResponse csrf;

    private static final Map<String, Object> SUBESTACAO = Map.of("nome", "SE Perfil", "latitude", "-25.4", "longitude", "-49.2",
            "tensaoNominalKv", "138", "capacidadeMva", "100", "dataInstalacao", "2024-01-15");

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

    private org.springframework.test.web.servlet.ResultActions enviar(String metodo, String rota, Object corpo) throws Exception {
        var requisicao = switch (metodo) {
            case "post" -> post(rota);
            case "put" -> put(rota);
            default -> patch(rota);
        };
        return mvc.perform(requisicao.session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)));
    }

    private long criarComoGerente(String rota, Map<String, Object> corpo) throws Exception {
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        var resposta = enviar("post", rota, corpo).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(resposta.getResponse().getContentAsString()).get("id").asLong();
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void devePermitirCadastrarAGerenteEOperador(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        long antes = subestacoes.count();

        enviar("post", "/api/subestacoes", SUBESTACAO).andExpect(status().isCreated());

        assertThat(subestacoes.count()).isEqualTo(antes + 1);
    }

    @Test
    void deveBloquearEscritaDoTecnicoEmTodosOsCadastrosDaRedeSemAlterarDados() throws Exception {
        long se1 = criarComoGerente("/api/subestacoes", SUBESTACAO);
        long se2 = criarComoGerente("/api/subestacoes", Map.of("nome", "SE Perfil 2", "latitude", "-25.5", "longitude", "-49.3",
                "tensaoNominalKv", "138", "capacidadeMva", "100", "dataInstalacao", "2024-01-15"));
        long alimentador = criarComoGerente("/api/alimentadores", Map.of("nome", "AL Perfil", "latitude", "-25.4", "longitude", "-49.2",
                "tensaoNominalKv", "13.8", "subestacaoId", se1, "dataInstalacao", "2024-01-15"));
        long transformador = criarComoGerente("/api/transformadores", Map.of("nome", "T Perfil", "latitude", "-25.4", "longitude", "-49.2",
                "capacidadeMva", "45", "alimentadorId", alimentador, "dataInstalacao", "2024-01-15"));
        long linha = criarComoGerente("/api/linhas-transmissao", Map.of("nome", "LT Perfil", "tensaoOperacaoKv", "138",
                "subestacaoOrigemId", se1, "subestacaoDestinoId", se2, "extensaoKm", "56", "dataInstalacao", "2024-01-15"));
        long consumidor = criarComoGerente("/api/consumidores", Map.of("nomeRazaoSocial", "Maria Perfil", "tipoPessoa", "PF", "documento", "52998224725"));
        long medidor = criarComoGerente("/api/medidores", Map.of("nome", "Medidor Perfil", "numeroSerie", "SN-PERFIL"));

        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        long totalSubestacoes = subestacoes.count();
        long totalConsumidores = consumidores.count();
        long totalMedidores = medidores.count();

        enviar("post", "/api/subestacoes", SUBESTACAO).andExpect(status().isForbidden());
        enviar("put", "/api/subestacoes/" + se1, SUBESTACAO).andExpect(status().isForbidden());
        enviar("post", "/api/alimentadores", Map.of("nome", "AL Negado", "latitude", "-25.4", "longitude", "-49.2",
                "tensaoNominalKv", "13.8", "subestacaoId", se1, "dataInstalacao", "2024-01-15")).andExpect(status().isForbidden());
        enviar("put", "/api/alimentadores/" + alimentador, Map.of("nome", "AL Negado", "latitude", "-25.4", "longitude", "-49.2",
                "tensaoNominalKv", "13.8", "subestacaoId", se1, "dataInstalacao", "2024-01-15")).andExpect(status().isForbidden());
        enviar("post", "/api/transformadores", Map.of("nome", "T Negado", "latitude", "-25.4", "longitude", "-49.2",
                "capacidadeMva", "45", "alimentadorId", alimentador, "dataInstalacao", "2024-01-15")).andExpect(status().isForbidden());
        enviar("put", "/api/transformadores/" + transformador, Map.of("nome", "T Negado", "latitude", "-25.4", "longitude", "-49.2",
                "capacidadeMva", "45", "alimentadorId", alimentador, "dataInstalacao", "2024-01-15")).andExpect(status().isForbidden());
        var corpoLinha = Map.<String, Object>of("nome", "LT Negada", "tensaoOperacaoKv", "138", "subestacaoOrigemId", se1,
                "subestacaoDestinoId", se2, "extensaoKm", "56", "dataInstalacao", "2024-01-15");
        enviar("post", "/api/linhas-transmissao", corpoLinha).andExpect(status().isForbidden());
        enviar("put", "/api/linhas-transmissao/" + linha, corpoLinha).andExpect(status().isForbidden());
        enviar("post", "/api/consumidores", Map.of("nomeRazaoSocial", "Negado", "tipoPessoa", "PF", "documento", "11144477735"))
                .andExpect(status().isForbidden());
        enviar("post", "/api/medidores", Map.of("nome", "Medidor Negado", "numeroSerie", "SN-NEGADO")).andExpect(status().isForbidden());
        enviar("patch", "/api/medidores/" + medidor + "/instalacao", Map.of("consumidorId", consumidor, "transformadorId", transformador,
                "latitude", "-25.44", "longitude", "-49.27", "dataInstalacao", "2024-05-10")).andExpect(status().isForbidden());

        assertThat(subestacoes.count()).isEqualTo(totalSubestacoes);
        assertThat(consumidores.count()).isEqualTo(totalConsumidores);
        assertThat(medidores.count()).isEqualTo(totalMedidores);
        assertThat(medidores.findById(medidor).orElseThrow().getConsumidor()).isNull();
    }

    @Test
    void devePermitirConsultaDoTecnicoEmTodosOsCadastrosDaRede() throws Exception {
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);

        for (String rota : List.of("/api/subestacoes", "/api/alimentadores", "/api/transformadores", "/api/linhas-transmissao",
                "/api/consumidores", "/api/medidores")) {
            mvc.perform(get(rota).session(sessao)).andExpect(status().isOk());
        }
    }
}
