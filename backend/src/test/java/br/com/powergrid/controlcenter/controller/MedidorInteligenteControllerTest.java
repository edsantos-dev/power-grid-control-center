package br.com.powergrid.controlcenter.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Value;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.repository.MedidorInteligenteRepository;
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
class MedidorInteligenteControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private MedidorInteligenteRepository repository;
    private Long consumidorId;
    private Long transformadorId;
    private Long alimentadorId;
    private Long subestacaoId;
    @Value("${FRONTEND_URL:http://localhost:5173}") private String frontendUrl;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        salvarCadeia();
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
    void deveCriarEstoqueEInstalarPreservandoIdentidade(EstadoOperacional estado) throws Exception {
        mockMvc.perform(get("/api/medidores").session(sessao)).andExpect(status().isOk()).andExpect(content().json("[]"));
        Map<String, Object> request = criacao();
        request.put("estadoOperacional", estado);
        MvcResult result = enviarPost(request, 201);
        MedidorResponseDTO criado = resposta(result);
        assertThat(criado.id()).isPositive();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/medidores/" + criado.id());
        assertThat(criado.nome()).isEqualTo("Medidor Central");
        assertThat(criado.numeroSerie()).isEqualTo("0001-AbC");
        assertThat(criado.estadoOperacional()).isEqualTo(estado == null ? EstadoOperacional.OPERACIONAL : estado);
        assertEstoque(criado);
        Map<String, Object> instalacao = instalacao();
        MvcResult atualizado = enviarPatch(criado.id(), instalacao, 200);
        MedidorResponseDTO instalado = resposta(atualizado);
        assertThat(instalado.id()).isEqualTo(criado.id());
        assertThat(instalado.numeroSerie()).isEqualTo(criado.numeroSerie());
        assertThat(instalado.estadoOperacional()).isEqualTo(criado.estadoOperacional());
        assertThat(instalado.consumidorId()).isEqualTo(consumidorId);
        assertThat(instalado.transformadorId()).isEqualTo(transformadorId);
        assertThat(instalado.alimentadorId()).isEqualTo(alimentadorId);
        assertThat(instalado.subestacaoId()).isEqualTo(subestacaoId);
        assertThat(instalado.latitude()).isEqualByComparingTo("-25.4284001");
        assertThat(instalado.longitude()).isEqualByComparingTo("-49.2733001");
        assertThat(instalado.dataInstalacao()).isEqualTo(LocalDate.now());
        assertThat(instalado.enderecoFornecimento()).isEqualTo("Rua das Flores, 100");
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/medidores/{id}", criado.id()).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json(atualizado.getResponse().getContentAsString()));
        MvcResult lista = mockMvc.perform(get("/api/medidores").session(sessao)).andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readValue(lista.getResponse().getContentAsString(), MedidorResponseDTO[].class))
                .extracting(MedidorResponseDTO::id).containsExactly(criado.id());
    }

    @Test
    void deveIgnorarDadosDeInstalacaoNoPost() throws Exception {
        Map<String, Object> request = criacao();
        request.putAll(instalacao());
        MedidorResponseDTO criado = resposta(enviarPost(request, 201));
        assertEstoque(criado);
        assertThat(criado.estadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
    }

    @Test
    void deveRejeitarSerieDuplicadaNormalizadaEDistinguirCapitalizacao() throws Exception {
        enviarPost(criacao(), 201);
        Map<String, Object> request = criacao();
        request.put("numeroSerie", "0001-AbC");
        enviarPost(request, 409);
        request.put("numeroSerie", "0001-abc");
        enviarPost(request, 201);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void deveTratarDuplicidadeDetectadaNoBanco() throws Exception {
        enviarPost(criacao(), 201);
        doReturn(false).when(repository).existsByNumeroSerie("0001-AbC");
        enviarPost(criacao(), 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "numeroSerie"})
    void deveValidarCamposDaCriacao(String campo) throws Exception {
        Map<String, Object> request = criacao();
        request.put(campo, null);
        enviarPost(request, 400);
        request.put(campo, "   ");
        enviarPost(request, 400);
        request.put(campo, "A".repeat(campo.equals("nome") ? 256 : 129));
        enviarPost(request, 400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarEstadoDesconhecido() throws Exception {
        Map<String, Object> request = criacao();
        request.put("estadoOperacional", "ESTOQUE");
        enviarPost(request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"consumidorId", "transformadorId", "latitude", "longitude", "dataInstalacao"})
    void deveRejeitarInstalacaoIncompleta(String campo) throws Exception {
        Map<String, Object> request = instalacao();
        request.put(campo, null);
        rejeitarInstalacao(request, 400);
    }

    @ParameterizedTest
    @CsvSource({"latitude,-90.0000001", "latitude,90.0000001", "longitude,-180.0000001",
            "longitude,180.0000001", "latitude,1.12345678", "longitude,1.12345678",
            "consumidorId,0", "consumidorId,-1", "transformadorId,0", "transformadorId,-1"})
    void deveRejeitarNumerosInvalidosSemAlterarEstoque(String campo, String valor) throws Exception {
        Map<String, Object> request = instalacao();
        request.put(campo, new BigDecimal(valor));
        rejeitarInstalacao(request, 400);
    }

    @Test
    void deveRejeitarDataFuturaOuInvalida() throws Exception {
        Map<String, Object> request = instalacao();
        request.put("dataInstalacao", LocalDate.now().plusDays(1).toString());
        rejeitarInstalacao(request, 400);
        request.put("dataInstalacao", "2026-02-30");
        rejeitarInstalacao(request, 400);
    }

    @ParameterizedTest
    @CsvSource({"-90,-180", "90,180"})
    void deveAceitarLimitesGeograficosEEnderecoOpcional(String latitude, String longitude) throws Exception {
        Long id = resposta(enviarPost(criacao(), 201)).id();
        Map<String, Object> request = instalacao();
        request.put("latitude", new BigDecimal(latitude));
        request.put("longitude", new BigDecimal(longitude));
        request.put("dataInstalacao", LocalDate.now().minusDays(1).toString());
        request.put("enderecoFornecimento", "   ");
        MedidorResponseDTO response = resposta(enviarPatch(id, request, 200));
        assertThat(response.latitude()).isEqualByComparingTo(latitude);
        assertThat(response.longitude()).isEqualByComparingTo(longitude);
        assertThat(response.enderecoFornecimento()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"consumidorId", "transformadorId"})
    void deveRejeitarReferenciaInexistenteSemInstalacaoParcial(String campo) throws Exception {
        Map<String, Object> request = instalacao();
        request.put(campo, Long.MAX_VALUE);
        rejeitarInstalacao(request, 404);
    }

    @Test
    void deveRetornar404ParaMedidorInexistente() throws Exception {
        mockMvc.perform(get("/api/medidores/{id}", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        enviarPatch(Long.MAX_VALUE, instalacao(), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ambos", "consumidor", "transformador"})
    void deveBloquearReinstalacaoOuVinculoParcial(String vinculo) throws Exception {
        Long id = resposta(enviarPost(criacao(), 201)).id();
        if (vinculo.equals("ambos")) enviarPatch(id, instalacao(), 200);
        else {
            MedidorInteligente medidor = repository.findById(id).orElseThrow();
            if (vinculo.equals("consumidor")) medidor.setConsumidor(entityManager.find(Consumidor.class, consumidorId));
            else medidor.setTransformador(entityManager.find(Transformador.class, transformadorId));
            repository.saveAndFlush(medidor);
        }
        String antes = mockMvc.perform(get("/api/medidores/{id}", id).session(sessao)).andReturn().getResponse().getContentAsString();
        enviarPatch(id, instalacao(), 409);
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/medidores/{id}", id).session(sessao)).andExpect(content().json(antes));
    }

    @Test
    void devePermitirPatchNoCorsDaOrigemConfigurada() throws Exception {
        mockMvc.perform(options("/api/medidores/1/instalacao").header("Origin", frontendUrl)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", frontendUrl))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mockMvc.perform(options("/api/medidores/1/instalacao").header("Origin", "https://origem-invalida.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveExigirSessaoECsrfNasEscritas() throws Exception {
        Long id = resposta(enviarPost(criacao(), 201)).id();
        mockMvc.perform(get("/api/medidores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/medidores/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/medidores").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criacao()))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/medidores/{id}/instalacao", id).session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(instalacao()))).andExpect(status().isForbidden());
        MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(patch("/api/medidores/{id}/instalacao", id)
                        .session((MockHttpSession) inicial.getRequest().getSession(false))
                        .header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(instalacao()))).andExpect(status().isUnauthorized());
        assertEstoque(resposta(mockMvc.perform(get("/api/medidores/{id}", id).session(sessao)).andReturn()));
    }

    private void rejeitarInstalacao(Map<String, Object> request, int esperado) throws Exception {
        Map<String, Object> criacao = criacao();
        criacao.put("numeroSerie", "TESTE-" + repository.count());
        Long id = resposta(enviarPost(criacao, 201)).id();
        enviarPatch(id, request, esperado);
        entityManager.flush();
        entityManager.clear();
        assertEstoque(resposta(mockMvc.perform(get("/api/medidores/{id}", id).session(sessao))
                .andExpect(status().isOk()).andReturn()));
    }

    private void assertEstoque(MedidorResponseDTO response) {
        assertThat(response.consumidorId()).isNull();
        assertThat(response.transformadorId()).isNull();
        assertThat(response.alimentadorId()).isNull();
        assertThat(response.subestacaoId()).isNull();
        assertThat(response.latitude()).isNull();
        assertThat(response.longitude()).isNull();
        assertThat(response.dataInstalacao()).isNull();
        assertThat(response.enderecoFornecimento()).isNull();
    }

    private MvcResult enviarPost(Map<String, Object> request, int esperado) throws Exception {
        return mockMvc.perform(post("/api/medidores").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private MvcResult enviarPatch(Long id, Map<String, Object> request, int esperado) throws Exception {
        return mockMvc.perform(patch("/api/medidores/{id}/instalacao", id).session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private MedidorResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), MedidorResponseDTO.class);
    }

    private Map<String, Object> criacao() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nome", "  Medidor Central  ");
        request.put("numeroSerie", "  0001-AbC  ");
        return request;
    }

    private Map<String, Object> instalacao() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("consumidorId", consumidorId);
        request.put("transformadorId", transformadorId);
        request.put("latitude", new BigDecimal("-25.4284001"));
        request.put("longitude", new BigDecimal("-49.2733001"));
        request.put("dataInstalacao", LocalDate.now().toString());
        request.put("enderecoFornecimento", "  Rua das Flores, 100  ");
        return request;
    }

    private void salvarCadeia() {
        Consumidor consumidor = new Consumidor();
        consumidor.setNomeRazaoSocial("Titular");
        consumidor.setTipoPessoa(Consumidor.TipoPessoa.PF);
        consumidor.setDocumento("52998224725");
        entityManager.persist(consumidor);
        consumidorId = consumidor.getId();
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação");
        subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        subestacao.setTensaoNominalKv(new BigDecimal("138"));
        subestacao.setCapacidadeMva(new BigDecimal("100"));
        entityManager.persist(subestacao);
        subestacaoId = subestacao.getId();
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador");
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
        alimentador.setSubestacao(subestacao);
        entityManager.persist(alimentador);
        alimentadorId = alimentador.getId();
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador");
        transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setCapacidadeMva(new BigDecimal("5"));
        transformador.setAlimentador(alimentador);
        entityManager.persist(transformador);
        transformadorId = transformador.getId();
        entityManager.flush();
    }
}
