package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.MedicaoLinhaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MedicaoLinhaControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private MedicaoLinhaRepository repository;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private FonteMedicao fonte;
    private Long linhaId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        linhaId = fonte.getLinha().getId();
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
    @ValueSource(strings = {"completa", "tensao", "corrente", "zero", "simulada"})
    void deveIngerirEPersistirLeitura(String tipo) throws Exception {
        Map<String, Object> request = valido();
        if (tipo.equals("tensao")) request.remove("correnteA");
        if (tipo.equals("corrente")) request.remove("tensaoKv");
        if (tipo.equals("zero")) {
            request.put("tensaoKv", BigDecimal.ZERO);
            request.put("correnteA", BigDecimal.ZERO);
        }
        if (tipo.equals("simulada")) fonte.setOrigem(OrigemMedicao.SIMULADA);
        request.put("recebidoEm", "2000-01-01T00:00:00Z");
        request.put("origem", "OUTRA");
        MvcResult result = enviar(linhaId, request, 201);
        MedicaoLinhaResponseDTO dto = objectMapper.readValue(result.getResponse().getContentAsString(), MedicaoLinhaResponseDTO.class);
        assertThat(dto.id()).isPositive();
        assertThat(dto.fonteId()).isEqualTo(fonte.getId());
        assertThat(dto.linhaId()).isEqualTo(linhaId);
        assertThat(dto.origem()).isEqualTo(fonte.getOrigem());
        assertThat(dto.recebidoEm().toInstant()).isEqualTo(AGORA);
        assertThat(dto.medidoEm().toInstant()).isEqualTo(Instant.parse("2026-10-28T13:00:00Z"));
        assertThat(dto.medidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(result.getResponse().getHeader("Location")).endsWith("/medicoes/" + dto.id());
        entityManager.flush();
        entityManager.clear();
        MedicaoLinha salvo = repository.findById(dto.id()).orElseThrow();
        assertThat(salvo.getRecebidoEm().toInstant()).isEqualTo(AGORA);
        assertThat(salvo.getMedidoEm().toInstant()).isEqualTo(dto.medidoEm().toInstant());
        if (dto.tensaoKv() == null) assertThat(salvo.getTensaoKv()).isNull();
        else assertThat(salvo.getTensaoKv()).isEqualByComparingTo(dto.tensaoKv());
        if (dto.correnteA() == null) assertThat(salvo.getCorrenteA()).isNull();
        else assertThat(salvo.getCorrenteA()).isEqualByComparingTo(dto.correnteA());
    }

    @Test
    void deveRejeitarReferenciasInexistentes() throws Exception {
        enviar(Long.MAX_VALUE, valido(), 404);
        Map<String, Object> request = valido();
        request.put("fonteId", Long.MAX_VALUE);
        enviar(linhaId, request, 404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarFonteDeOutraLinha() throws Exception {
        enviar(linha().getId(), valido(), 400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarAmbasGrandezasAusentes() throws Exception {
        Map<String, Object> request = valido();
        request.put("tensaoKv", null);
        request.put("correnteA", null);
        rejeitar(request);
    }

    @ParameterizedTest
    @CsvSource({"tensaoKv,-1", "correnteA,-1", "tensaoKv,1.1234567", "correnteA,1.1234567",
            "tensaoKv,10000000000000", "correnteA,10000000000000", "fonteId,0", "fonteId,-1"})
    void deveRejeitarValoresInvalidos(String campo, String valor) throws Exception {
        Map<String, Object> request = valido();
        request.put(campo, new BigDecimal(valor));
        rejeitar(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonteId", "medidoEm"})
    void deveRejeitarObrigatoriosNulos(String campo) throws Exception {
        Map<String, Object> request = valido();
        request.put(campo, null);
        rejeitar(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-29T12:00:00", "invalido", "2026-10-29T12:01:00.000001Z"})
    void deveRejeitarInstanteInvalidoOuAcimaDaTolerancia(String instante) throws Exception {
        Map<String, Object> request = valido();
        request.put("medidoEm", instante);
        rejeitar(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-29T12:01:00Z", "2026-10-29T09:01:00-03:00", "2026-10-29T12:00:00Z"})
    void deveAceitarLimiteInclusivoComOffset(String instante) throws Exception {
        Map<String, Object> request = valido();
        request.put("medidoEm", instante);
        enviar(linhaId, request, 201);
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        MvcResult anonimo = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        MockHttpSession outra = (MockHttpSession) anonimo.getRequest().getSession(false);
        CsrfResponse token = objectMapper.readValue(anonimo.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post(url(linhaId)).session(outra).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(valido())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url(linhaId)).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valido()))).andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    private void rejeitar(Map<String, Object> request) throws Exception {
        enviar(linhaId, request, 400);
        assertThat(repository.count()).isZero();
    }

    private String url(Long id) { return "/api/linhas-transmissao/" + id + "/medicoes"; }

    private MvcResult enviar(Long id, Map<String, Object> request, int esperado) throws Exception {
        var result = mockMvc.perform(post(url(id)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado));
        if (esperado >= 400) result.andExpect(jsonPath("$.status").value(esperado))
                .andExpect(jsonPath("$.path").value(url(id))).andExpect(jsonPath("$.message").isNotEmpty());
        return result.andReturn();
    }

    private Map<String, Object> valido() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fonteId", fonte.getId());
        request.put("medidoEm", "2026-10-28T10:00:00-03:00");
        request.put("tensaoKv", new BigDecimal("138.123456"));
        request.put("correnteA", new BigDecimal("50.654321"));
        return request;
    }
    private LinhaTransmissao linha() {
        Subestacao a = new Subestacao();
        a.setNome("Origem");
        a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(a);
        Subestacao b = new Subestacao();
        b.setNome("Destino");
        b.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(b);
        LinhaTransmissao linha = new LinhaTransmissao();
        linha.setNome("Linha");
        linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setSubestacaoOrigem(a);
        linha.setSubestacaoDestino(b);
        linha.setExtensaoKm(new BigDecimal("100"));
        linha.setTensaoOperacaoKv(new BigDecimal("138"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

    private FonteMedicao fonte() {
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador("SENSOR-01");
        fonte.setLinha(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }

}
