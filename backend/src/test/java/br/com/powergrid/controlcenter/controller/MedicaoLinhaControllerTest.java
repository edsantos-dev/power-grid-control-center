package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
        linhaId = fonte.getAtivo().getId();
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

    @ParameterizedTest
    @ValueSource(strings = {"ativaNegativa", "reativaNegativa", "ambas", "zero", "limite"})
    void deveIngerirEConsultarPotenciasComReferencia(String caso) throws Exception {
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoOrigem());
        Long referenciaId = fonte.getSubestacaoReferencia().getId();
        Map<String, Object> request = valido();
        request.put("tensaoKv", null); request.put("correnteA", null);
        BigDecimal ativa = caso.equals("reativaNegativa") ? null : new BigDecimal("-12.123456");
        BigDecimal reativa = caso.equals("ativaNegativa") ? null : new BigDecimal("-3.654321");
        if (caso.equals("zero")) { ativa = BigDecimal.ZERO; reativa = null; }
        if (caso.equals("limite")) {
            ativa = new BigDecimal("-9999999999999.999999");
            reativa = new BigDecimal("9999999999999.999999");
        }
        request.put("potenciaAtivaMw", ativa); request.put("potenciaReativaMvar", reativa);
        MedicaoLinhaResponseDTO dto = objectMapper.readValue(enviar(linhaId, request, 201)
                .getResponse().getContentAsString(), MedicaoLinhaResponseDTO.class);
        assertThat(dto.potenciaAtivaMw()).isEqualTo(ativa);
        assertThat(dto.potenciaReativaMvar()).isEqualTo(reativa);
        assertThat(dto.subestacaoReferenciaId()).isEqualTo(referenciaId);
        entityManager.flush(); entityManager.clear();
        MedicaoLinha salvo = repository.findById(dto.id()).orElseThrow();
        if (ativa == null) assertThat(salvo.getPotenciaAtivaMw()).isNull();
        else assertThat(salvo.getPotenciaAtivaMw()).isEqualByComparingTo(ativa);
        if (reativa == null) assertThat(salvo.getPotenciaReativaMvar()).isNull();
        else assertThat(salvo.getPotenciaReativaMvar()).isEqualByComparingTo(reativa);
        mockMvc.perform(get(url(linhaId)).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dto.id()))
                .andExpect(jsonPath("$.content[0].subestacaoReferenciaId").value(referenciaId))
                .andExpect(jsonPath("$.content[0].tensaoKv").isEmpty())
                .andExpect(jsonPath("$.content[0].correnteA").isEmpty());
        String json = mockMvc.perform(get(url(linhaId)).session(sessao)).andReturn().getResponse().getContentAsString();
        br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO> pagina = objectMapper.readValue(json,
                new tools.jackson.core.type.TypeReference<br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO>>() {});
        var item = pagina.content().getFirst();
        if (ativa == null) assertThat(item.potenciaAtivaMw()).isNull();
        else assertThat(item.potenciaAtivaMw()).isEqualByComparingTo(ativa);
        if (reativa == null) assertThat(item.potenciaReativaMvar()).isNull();
        else assertThat(item.potenciaReativaMvar()).isEqualByComparingTo(reativa);
    }

    @ParameterizedTest
    @CsvSource({"potenciaAtivaMw,1.1234567", "potenciaReativaMvar,-1.1234567",
            "potenciaAtivaMw,10000000000000", "potenciaReativaMvar,-10000000000000"})
    void deveRejeitarPrecisaoExcedidaDaPotencia(String campo, String valor) throws Exception {
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoOrigem());
        Map<String, Object> request = valido(); request.put(campo, new BigDecimal(valor));
        rejeitar(request);
    }

    @Test
    void deveRejeitarPotenciaSemReferenciaOuComReferenciaForaDaLinha() throws Exception {
        Map<String, Object> request = valido(); request.put("potenciaAtivaMw", BigDecimal.ONE.negate());
        rejeitar(request);
        fonte.setSubestacaoReferencia(linha().getSubestacaoOrigem());
        rejeitar(request);
    }

    @Test
    void deveAceitarQuatroGrandezasComSinaisIndependentes() throws Exception {
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoDestino());
        Map<String, Object> request = valido();
        request.put("potenciaAtivaMw", new BigDecimal("5"));
        request.put("potenciaReativaMvar", new BigDecimal("-2"));
        enviar(linhaId, request, 201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"60.000000", "58.000000", "62.000000", "0.000001", "999.999999"})
    void deveIngerirFrequenciaIsolada(String valor) throws Exception {
        Map<String, Object> request = valido();
        request.put("tensaoKv", null); request.put("correnteA", null);
        request.put("frequenciaHz", new BigDecimal(valor));
        MedicaoLinhaResponseDTO dto = resposta(enviar(linhaId, request, 201));
        assertThat(dto.frequenciaHz()).isEqualByComparingTo(valor);
        assertThat(dto.fatorPotencia()).isNull();
        assertThat(dto.situacaoFatorPotencia()).isEqualTo(SituacaoFatorPotencia.DADOS_INSUFICIENTES);
        entityManager.flush(); entityManager.clear();
        assertThat(repository.findById(dto.id()).orElseThrow().getFrequenciaHz()).isEqualByComparingTo(valor);
        var consultado = consultarPrimeiro();
        assertThat(consultado.frequenciaHz()).isEqualByComparingTo(valor);
        assertThat(consultado.fatorPotencia()).isNull();
        assertThat(consultado.situacaoFatorPotencia()).isEqualTo(dto.situacaoFatorPotencia());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.000001", "60.1234567", "1000"})
    void deveRejeitarFrequenciaInvalida(String valor) throws Exception {
        Map<String, Object> request = valido();
        request.put("frequenciaHz", new BigDecimal(valor));
        rejeitar(request);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "3,4,0.600000,CALCULADO", "-3,4,0.600000,CALCULADO", "3,-4,0.600000,CALCULADO",
            "1,1,0.707107,CALCULADO", "2,3,0.554700,CALCULADO", "0,2,0.000000,CALCULADO",
            "-2,0,1.000000,CALCULADO", "0,0,NULL,POTENCIA_APARENTE_NULA",
            "NULL,4,NULL,DADOS_INSUFICIENTES", "3,NULL,NULL,DADOS_INSUFICIENTES",
            "NULL,NULL,NULL,DADOS_INSUFICIENTES"
    }, nullValues = "NULL")
    void deveCalcularFatorPotenciaNoPostENaConsulta(String ativa, String reativa, String esperado, String situacao) throws Exception {
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoOrigem());
        Map<String, Object> request = valido();
        request.put("tensaoKv", null); request.put("correnteA", null);
        request.put("potenciaAtivaMw", ativa == null ? null : new BigDecimal(ativa));
        request.put("potenciaReativaMvar", reativa == null ? null : new BigDecimal(reativa));
        request.put("frequenciaHz", new BigDecimal("60"));
        request.put("fatorPotencia", new BigDecimal("0.123456"));
        request.put("situacaoFatorPotencia", "VALOR_DO_CLIENTE");
        MedicaoLinhaResponseDTO criado = resposta(enviar(linhaId, request, 201));
        entityManager.flush(); entityManager.clear();
        for (MedicaoLinhaResponseDTO dto : new MedicaoLinhaResponseDTO[] {criado, consultarPrimeiro()}) {
            assertThat(dto.situacaoFatorPotencia()).isEqualTo(SituacaoFatorPotencia.valueOf(situacao));
            if (esperado == null) assertThat(dto.fatorPotencia()).isNull();
            else {
                assertThat(dto.fatorPotencia()).isEqualTo(new BigDecimal(esperado));
                assertThat(dto.fatorPotencia()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            }
        }
    }

    @Test
    void naoDeveCombinarPotenciasDeLeiturasDiferentes() throws Exception {
        fonte.setSubestacaoReferencia(((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo())).getSubestacaoOrigem());
        Map<String, Object> request = valido();
        request.put("tensaoKv", null); request.put("correnteA", null);
        request.put("potenciaAtivaMw", new BigDecimal("3"));
        resposta(enviar(linhaId, request, 201));
        request.put("potenciaAtivaMw", null); request.put("potenciaReativaMvar", new BigDecimal("4"));
        resposta(enviar(linhaId, request, 201));
        String json = mockMvc.perform(get(url(linhaId)).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO> pagina = objectMapper.readValue(json,
                new tools.jackson.core.type.TypeReference<br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO>>() {});
        assertThat(pagina.content()).hasSize(2).allSatisfy(dto -> {
            assertThat(dto.fatorPotencia()).isNull();
            assertThat(dto.situacaoFatorPotencia()).isEqualTo(SituacaoFatorPotencia.DADOS_INSUFICIENTES);
        });
    }

    private MedicaoLinhaResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), MedicaoLinhaResponseDTO.class);
    }

    private MedicaoLinhaResponseDTO consultarPrimeiro() throws Exception {
        String json = mockMvc.perform(get(url(linhaId)).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO> pagina = objectMapper.readValue(json,
                new tools.jackson.core.type.TypeReference<br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoLinhaResponseDTO>>() {});
        return pagina.content().getFirst();
    }

    @Test
    void deveRejeitarFonteDeTransformadorNaApiDeLinhas() throws Exception {
        var linha = ((LinhaTransmissao) org.hibernate.Hibernate.unproxy(fonte.getAtivo()));
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(linha.getSubestacaoOrigem()); entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador"); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(transformador); fonte.setAtivo(transformador); entityManager.flush();
        enviar(linhaId, valido(), 400);
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
        fonte.setAtivo(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }

}
