package br.com.powergrid.controlcenter.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.ConsumidorResponseDTO;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.repository.ConsumidorRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ConsumidorControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private ConsumidorRepository repository;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
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
    @CsvSource({
            "PF, 529.982.247-25, 52998224725", "PF, 01234567890, 01234567890",
            "PJ, 11.222.333/0001-81, 11222333000181",
            "PJ, 12.abc.345/01de-35, 12ABC34501DE35",
            "PJ, 12ABC34501DE35, 12ABC34501DE35",
            "PJ, 00.000.000/E08G-12, 00000000E08G12"
    })
    void deveCriarNormalizarEConsultar(String tipo, String documento, String normalizado) throws Exception {
        mockMvc.perform(get("/api/consumidores").session(sessao))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        Map<String, Object> request = valido(tipo, "  " + documento + "  ");
        MvcResult result = enviar(request, 201);
        ConsumidorResponseDTO response = resposta(result);
        assertThat(response.id()).isPositive();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("http://localhost/api/consumidores/" + response.id());
        assertThat(response.documento()).isEqualTo(normalizado);
        assertThat(response.nomeRazaoSocial()).isEqualTo("Titular de teste");
        assertThat(response.tipoPessoa().name()).isEqualTo(tipo);
        assertThat(response.enderecoCorrespondencia()).isEqualTo("Rua das Flores, 100");
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(response.id()).orElseThrow().getDocumento()).isEqualTo(normalizado);
        mockMvc.perform(get("/api/consumidores/{id}", response.id()).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
        MvcResult lista = mockMvc.perform(get("/api/consumidores").session(sessao))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readValue(lista.getResponse().getContentAsString(), ConsumidorResponseDTO[].class))
                .containsExactly(response);
    }

    @ParameterizedTest
    @CsvSource({
            "PF, 52998224724", "PF, 52998224715", "PJ, 11222333000180",
            "PJ, 12ABC34501DE34", "PJ, 12ABC34501DE25",
            "PF, 11111111111", "PJ, 00000000000000",
            "PF, 5299822472", "PJ, 12ABC34501DE3",
            "PF, 12ABC34501DE35", "PJ, 52998224725",
            "PF, 529x98224725", "PJ, 12ABC34501DE3A",
            "PF, 529-982-247.25", "PJ, 12.ABC345/01DE-35",
            "PF, +52998224725", "PJ, 12ABC 34501DE35", "PJ, 12ÁBC34501DE35"
    })
    void deveRejeitarDocumentoInvalido(String tipo, String documento) throws Exception {
        enviar(valido(tipo, documento), 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"PF, 529.982.247-25, 52998224725", "PJ, 12.abc.345/01de-35, 12ABC34501DE35"})
    void deveRejeitarDuplicidadeAposNormalizar(String tipo, String primeiro, String segundo) throws Exception {
        ConsumidorResponseDTO salvo = resposta(enviar(valido(tipo, primeiro), 201));
        enviar(valido(tipo, segundo), 409);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(salvo.id()).orElseThrow().getDocumento()).isEqualTo(segundo);
    }

    @Test
    void deveRetornar409QuandoBancoDetectarDuplicidadeAposConsultaPrevia() throws Exception {
        enviar(valido("PF", "52998224725"), 201);
        doReturn(false).when(repository).existsByDocumento("52998224725");
        enviar(valido("PF", "529.982.247-25"), 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nomeRazaoSocial", "tipoPessoa", "documento"})
    void deveRejeitarObrigatorioNulo(String campo) throws Exception {
        Map<String, Object> request = valido("PF", "52998224725");
        request.put(campo, null);
        enviar(request, 400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarNomeVazioLongoDocumentoVazioETipoDesconhecido() throws Exception {
        for (String nome : new String[] {"   ", "A".repeat(256)}) {
            Map<String, Object> request = valido("PF", "52998224725");
            request.put("nomeRazaoSocial", nome);
            enviar(request, 400);
        }
        enviar(valido("PF", "   "), 400);
        enviar(valido("XX", "52998224725"), 400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void devePermitirEnderecoNuloOuVazio() throws Exception {
        Map<String, Object> request = valido("PF", "52998224725");
        request.put("enderecoCorrespondencia", null);
        assertThat(resposta(enviar(request, 201)).enderecoCorrespondencia()).isNull();
        request = valido("PJ", "12ABC34501DE35");
        request.put("enderecoCorrespondencia", "   ");
        assertThat(resposta(enviar(request, 201)).enderecoCorrespondencia()).isNull();
    }

    @Test
    void deveRetornar404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/api/consumidores/{id}", Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveExigirAutenticacaoECsrf() throws Exception {
        mockMvc.perform(get("/api/consumidores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/consumidores/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/consumidores").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valido("PF", "52998224725"))))
                .andExpect(status().isForbidden());
        MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/consumidores")
                        .session((MockHttpSession) inicial.getRequest().getSession(false))
                        .header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valido("PF", "52998224725"))))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    private Map<String, Object> valido(String tipo, String documento) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nomeRazaoSocial", "  Titular de teste  ");
        request.put("tipoPessoa", tipo);
        request.put("documento", documento);
        request.put("enderecoCorrespondencia", "  Rua das Flores, 100  ");
        return request;
    }

    private MvcResult enviar(Map<String, Object> request, int esperado) throws Exception {
        return mockMvc.perform(post("/api/consumidores").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private ConsumidorResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), ConsumidorResponseDTO.class);
    }
}
