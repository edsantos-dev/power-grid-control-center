package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.MedicaoTransformadorRepository;
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
class MedicaoTransformadorControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private MedicaoTransformadorRepository repository;
    @Autowired private br.com.powergrid.controlcenter.repository.AlertaSobrecargaRepository alertas;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private FonteMedicao fonte;
    private Long transformadorId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        transformadorId = fonte.getAtivo().getId();
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
    @CsvSource({"0,10,0.000000", "8.5,10,85.000000", "10,10,100.000000", "10.8,10,108.000000",
            "1,3,33.333333", "0.000001,200,0.000000", "0.000003,200,0.000002"})
    void deveIngerirConsultarEArredondarCarga(String potencia, String capacidade, String percentual) throws Exception {
        entityManager.find(Transformador.class, transformadorId).setCapacidadeMva(new BigDecimal(capacidade));
        Map<String, Object> request = valido(); request.put("potenciaAparenteMva", new BigDecimal(potencia));
        request.put("capacidadeReferenciaMva", new BigDecimal("999"));
        request.put("cargaPercentual", new BigDecimal("1")); request.put("recebidoEm", "2000-01-01T00:00:00Z");
        MvcResult result = enviar(transformadorId, request, 201);
        MedicaoTransformadorResponseDTO dto = resposta(result);
        assertThat(result.getResponse().getHeader("Location")).endsWith("/medicoes/" + dto.id());
        entityManager.flush(); entityManager.clear();
        for (var item : new MedicaoTransformadorResponseDTO[] {dto, consultar().content().getFirst()}) {
            assertThat(item.potenciaAparenteMva()).isEqualByComparingTo(potencia);
            assertThat(item.capacidadeReferenciaMva()).isEqualByComparingTo(capacidade);
            assertThat(item.cargaPercentual()).isEqualTo(new BigDecimal(percentual));
            assertThat(item.recebidoEm().toInstant()).isEqualTo(AGORA);
            assertThat(item.medidoEm().toInstant()).isEqualTo(Instant.parse("2026-10-28T13:00:00Z"));
            assertThat(item.transformadorId()).isEqualTo(transformadorId);
            assertThat(item.fonteId()).isEqualTo(fonte.getId());
        }
    }

    @Test
    void devePreservarSnapshotAoAlterarCapacidade() throws Exception {
        var antigo = resposta(enviar(transformadorId, valido(), 201));
        entityManager.find(Transformador.class, transformadorId).setCapacidadeMva(new BigDecimal("20"));
        var novo = resposta(enviar(transformadorId, valido(), 201));
        entityManager.flush(); entityManager.clear();
        var lista = consultar().content();
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).id()).isEqualTo(novo.id());
        assertThat(lista.get(0).capacidadeReferenciaMva()).isEqualByComparingTo("20");
        assertThat(lista.get(0).cargaPercentual()).isEqualByComparingTo("42.5");
        assertThat(lista.get(1).id()).isEqualTo(antigo.id());
        assertThat(lista.get(1).capacidadeReferenciaMva()).isEqualByComparingTo("10");
        assertThat(lista.get(1).cargaPercentual()).isEqualByComparingTo("85");
    }

    @Test
    void deveRetornar404ParaTransformadorOuFonteInexistentes() throws Exception {
        enviar(Long.MAX_VALUE, valido(), 404);
        var request = valido(); request.put("fonteId", Long.MAX_VALUE); enviar(transformadorId, request, 404);
        mockMvc.perform(get(url(Long.MAX_VALUE)).session(sessao)).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarFonteDeLinhaEOuDeOutroTransformador() throws Exception {
        fonte.setAtivo(linha()); entityManager.flush();
        enviar(transformadorId, valido(), 400);
        var atual = entityManager.find(Transformador.class, transformadorId);
        Transformador outro = new Transformador(); outro.setNome("Outro"); outro.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        outro.setAlimentador(atual.getAlimentador()); outro.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(outro); fonte.setAtivo(outro); entityManager.flush();
        enviar(transformadorId, valido(), 400); assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonteId", "medidoEm", "potenciaAparenteMva"})
    void deveRejeitarObrigatoriosNulos(String campo) throws Exception {
        var request = valido(); request.put(campo, null); enviar(transformadorId, request, 400);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "8.1234567", "10000000000000"})
    void deveRejeitarPotenciaInvalida(String valor) throws Exception {
        var request = valido(); request.put("potenciaAparenteMva", new BigDecimal(valor));
        enviar(transformadorId, request, 400); assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-29T12:01:00Z", "2026-10-29T09:01:00-03:00"})
    void deveAceitarLimiteFuturoInclusivo(String instante) throws Exception {
        var request = valido(); request.put("medidoEm", instante); enviar(transformadorId, request, 201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-29T12:01:00.000001Z", "2026-10-29T12:00:00", "invalido"})
    void deveRejeitarInstanteInvalido(String instante) throws Exception {
        var request = valido(); request.put("medidoEm", instante); enviar(transformadorId, request, 400);
    }

    @Test
    void deveCadastrarFonteSimuladaComUnicidadeGlobal() throws Exception {
        var req = Map.of("identificador", "  NOVA-FONTE  ", "origem", "SIMULADA");
        mockMvc.perform(post(fontesUrl(transformadorId)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.transformadorId").value(transformadorId))
                .andExpect(jsonPath("$.identificador").value("NOVA-FONTE"));
        fontePost(req, 409);
        FonteMedicao fonteLinha = new FonteMedicao(); fonteLinha.setIdentificador("FONTE-LINHA");
        fonteLinha.setAtivo(linha()); fonteLinha.setOrigem(OrigemMedicao.SIMULADA); entityManager.persist(fonteLinha);
        entityManager.flush(); fontePost(Map.of("identificador", "FONTE-LINHA", "origem", "SIMULADA"), 409);
        mockMvc.perform(post(fontesUrl(Long.MAX_VALUE)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isNotFound());
        fontePost(Map.of("identificador", "NOVA", "origem", "SIMULADA", "subestacaoReferenciaId", 1), 400);
        fontePost(Map.of("identificador", "   ", "origem", "SIMULADA"), 400);
    }

    @Test
    void devePaginarFiltrarEIsolarAtivos() throws Exception {
        var req = valido(); req.put("medidoEm", "2026-10-28T09:00:00Z"); enviar(transformadorId, req, 201);
        req.put("medidoEm", "2026-10-28T10:00:00Z"); var primeiro = resposta(enviar(transformadorId, req, 201));
        var segundo = resposta(enviar(transformadorId, req, 201));
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content[0].id").value(segundo.id()));
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(primeiro.id()));
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("dataInicio", "2026-10-28T07:00:00-03:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("dataFim", "2026-10-28T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        Transformador outro = new Transformador(); outro.setNome("Outro"); outro.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        outro.setCapacidadeMva(new BigDecimal("10"));
        outro.setAlimentador(entityManager.find(Transformador.class, transformadorId).getAlimentador()); entityManager.persist(outro);
        mockMvc.perform(get(url(outro.getId())).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.size").value(20));
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|1.5", "size|0", "size|101", "sort|medidoEm,asc", "dataInicio|invalido"}, delimiter = '|')
    void deveRejeitarConsultaInvalida(String campo, String valor) throws Exception {
        mockMvc.perform(get(url(transformadorId)).session(sessao).param(campo, valor)).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarParametrosVaziosDuplicadosEPeriodoInvertido() throws Exception {
        for (String campo : new String[] {"page", "size", "sort", "dataInicio", "dataFim"}) {
            mockMvc.perform(get(url(transformadorId)).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
            mockMvc.perform(get(url(transformadorId)).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(url(transformadorId)).session(sessao).param("dataInicio", "2026-10-28T10:00:00Z")
                .param("dataFim", "2026-10-28T10:00:00Z")).andExpect(status().isBadRequest());
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        mockMvc.perform(get(url(transformadorId))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(url(transformadorId)).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valido()))).andExpect(status().isForbidden());
        mockMvc.perform(post(fontesUrl(transformadorId)).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identificador", "NOVA", "origem", "SIMULADA"))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"8.5,0", "10,0", "10.8,1"})
    void deveGerarAlertaSomenteAcimaDeCem(String potencia, long total) throws Exception {
        var request = valido(); request.put("potenciaAparenteMva", new BigDecimal(potencia));
        var medicao = resposta(enviar(transformadorId, request, 201));
        assertThat(alertas.count()).isEqualTo(total);
        if (total == 1) {
            var alerta = alertas.findAll().getFirst();
            assertThat(alerta.getMedicao().getId()).isEqualTo(medicao.id());
            assertThat(alerta.getAtivo().getId()).isEqualTo(transformadorId);
            assertThat(alerta.getCargaPercentual()).isEqualByComparingTo("108");
            assertThat(alerta.getLimiarPercentual()).isEqualByComparingTo("100");
            assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ATIVO);
            assertThat(alerta.getGeradoEm().toInstant()).isEqualTo(AGORA);
        }
    }

    private String url(Long id) { return "/api/transformadores/" + id + "/medicoes"; }
    private String fontesUrl(Long id) { return "/api/transformadores/" + id + "/fontes"; }
    private void fontePost(Map<String, ?> req, int esperado) throws Exception {
        mockMvc.perform(post(fontesUrl(transformadorId)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().is(esperado));
    }
    private MvcResult enviar(Long id, Map<String, Object> request, int esperado) throws Exception {
        var result = mockMvc.perform(post(url(id)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().is(esperado));
        if (esperado >= 400) result.andExpect(jsonPath("$.status").value(esperado));
        return result.andReturn();
    }
    private MedicaoTransformadorResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), MedicaoTransformadorResponseDTO.class);
    }
    private br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoTransformadorResponseDTO> consultar() throws Exception {
        var result = mockMvc.perform(get(url(transformadorId)).session(sessao)).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new tools.jackson.core.type.TypeReference<br.com.powergrid.controlcenter.dto.PageResponseDTO<MedicaoTransformadorResponseDTO>>() {});
    }
    private Map<String, Object> valido() {
        Map<String, Object> request = new LinkedHashMap<>(); request.put("fonteId", fonte.getId());
        request.put("medidoEm", "2026-10-28T10:00:00-03:00"); request.put("potenciaAparenteMva", new BigDecimal("8.5"));
        return request;
    }
    private FonteMedicao fonte() {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação"); subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(subestacao); entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador"); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(transformador);
        FonteMedicao fonte = new FonteMedicao(); fonte.setIdentificador("SIMULADOR-TRAFO");
        fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setAtivo(transformador);
        entityManager.persist(fonte); entityManager.flush(); return fonte;
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

}
