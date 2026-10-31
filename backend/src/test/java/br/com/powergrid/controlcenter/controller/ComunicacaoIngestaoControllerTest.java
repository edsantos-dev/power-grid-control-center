package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.AlertaComunicacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ComunicacaoIngestaoControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private AlertaComunicacaoRepository alertas;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00.123456789Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", "operador@powergrid.local")
                .param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LINHA", "TRANSFORMADOR"})
    void deveRegistrarRecebimentoSemUsarDataHistoricaDoSensor(String tipo) throws Exception {
        var fonte = fonte(tipo); Long id = fonte.getId();
        var recebido = ingerir(fonte, tipo, payload(fonte, tipo), 201);
        entityManager.flush(); entityManager.clear();
        var salva = entityManager.find(FonteMedicao.class, id);
        assertThat(recebido.toInstant()).isEqualTo(AGORA.truncatedTo(ChronoUnit.MICROS));
        assertThat(recebido.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(salva.getUltimaComunicacaoEm().toInstant()).isEqualTo(recebido.toInstant());
        assertThat(salva.getUltimaComunicacaoEm().toInstant()).isNotEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(alertas.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LINHA", "TRANSFORMADOR"})
    void deveRestabelecerPreservarHistoricoEPermitirNovaPerda(String tipo) throws Exception {
        var fonte = fonte(tipo);
        avaliar();
        var alerta = alertas.findAll().getFirst(); Long alertaId = alerta.getId();
        var detectado = alerta.getDetectadoEm().toInstant();
        var inicio = alerta.getMonitoradaDesde().toInstant();
        assertThat(alerta.getUltimaComunicacaoEm()).isNull();
        var recebido = ingerir(fonte, tipo, payload(fonte, tipo), 201);
        entityManager.flush(); entityManager.clear();
        var salvo = alertas.findById(alertaId).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusComunicacao.RESTABELECIDO);
        assertThat(salvo.getRestabelecidoEm().toInstant()).isEqualTo(recebido.toInstant());
        assertThat(salvo.getDetectadoEm().toInstant()).isEqualTo(detectado);
        assertThat(salvo.getMonitoradaDesde().toInstant()).isEqualTo(inicio);
        assertThat(salvo.getUltimaComunicacaoEm()).isNull();
        mockMvc.perform(get("/api/alertas-comunicacao").session(sessao).param("status", "RESTABELECIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        when(clock.instant()).thenReturn(AGORA.plusSeconds(10));
        ingerir(fonte, tipo, payload(fonte, tipo), 201);
        entityManager.flush(); entityManager.clear();
        assertThat(alertas.findById(alertaId).orElseThrow().getRestabelecidoEm().toInstant()).isEqualTo(recebido.toInstant());
        avaliar(); assertThat(alertas.count()).isEqualTo(1);
        when(clock.instant()).thenReturn(AGORA.plusSeconds(311));
        avaliar();
        assertThat(alertas.count()).isEqualTo(2);
        assertThat(alertas.findAll().stream().filter(a -> a.getStatus() == StatusComunicacao.ATIVO).count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LINHA", "TRANSFORMADOR"})
    void requisicoesInvalidasNaoDevemRestabelecerNemAtualizarContato(String tipo) throws Exception {
        var fonte = fonte(tipo);
        fonte.setUltimaComunicacaoEm(AGORA.minusSeconds(301).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)); entityManager.flush();
        avaliar();
        var negativo = payload(fonte, tipo);
        negativo.put(tipo.equals("LINHA") ? "tensaoKv" : "potenciaAparenteMva", -1);
        ingerir(fonte, tipo, negativo, 400);
        var futuro = payload(fonte, tipo); futuro.put("medidoEm", AGORA.plusSeconds(61).toString());
        ingerir(fonte, tipo, futuro, 400);
        entityManager.flush(); entityManager.clear();
        assertThat(entityManager.find(FonteMedicao.class, fonte.getId()).getUltimaComunicacaoEm().toInstant())
                .isEqualTo(AGORA.minusSeconds(301).truncatedTo(ChronoUnit.MICROS));
        var alerta = alertas.findAll().getFirst();
        assertThat(alerta.getStatus()).isEqualTo(StatusComunicacao.ATIVO);
        assertThat(alerta.getRestabelecidoEm()).isNull();
        assertThat(entityManager.createQuery("select count(m) from MedicaoLinha m", Long.class).getSingleResult()).isZero();
        assertThat(entityManager.createQuery("select count(m) from MedicaoTransformador m", Long.class).getSingleResult()).isZero();
    }

    private void avaliar() throws Exception {
        mockMvc.perform(post("/api/jobs/avaliar-comunicacao").session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
    }

    private Map<String, Object> payload(FonteMedicao fonte, String tipo) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("fonteId", fonte.getId()); payload.put("medidoEm", "2026-01-01T00:00:00Z");
        payload.put(tipo.equals("LINHA") ? "tensaoKv" : "potenciaAparenteMva", tipo.equals("LINHA") ? 100 : 8);
        return payload;
    }

    private OffsetDateTime ingerir(FonteMedicao fonte, String tipo, Map<String, Object> payload, int esperado) throws Exception {
        String base = tipo.equals("LINHA") ? "/api/linhas-transmissao/" : "/api/transformadores/";
        var result = mockMvc.perform(post(base + fonte.getAtivo().getId() + "/medicoes").session(sessao)
                .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))).andExpect(status().is(esperado)).andReturn();
        return esperado == 201 ? OffsetDateTime.parse(objectMapper.readTree(result.getResponse().getContentAsString()).get("recebidoEm").asString()) : null;
    }

    private FonteMedicao fonte(String tipo) {
        var linha = linha();
        AtivoRede ativo = linha;
        if (tipo.equals("TRANSFORMADOR")) {
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador");
            alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); alimentador.setSubestacao(linha.getSubestacaoOrigem());
            entityManager.persist(alimentador);
            var trafo = new Transformador(); trafo.setNome("Transformador"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            trafo.setAlimentador(alimentador); trafo.setCapacidadeMva(BigDecimal.TEN); entityManager.persist(trafo); ativo = trafo;
        }
        var fonte = new FonteMedicao(); fonte.setAtivo(ativo); fonte.setIdentificador("FONTE-INGESTAO");
        fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setMonitoradaDesde(AGORA.minusSeconds(3600).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
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
        linha.setTensaoOperacaoKv(new BigDecimal("100"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

}
