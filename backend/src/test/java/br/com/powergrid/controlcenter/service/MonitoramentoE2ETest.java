package br.com.powergrid.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.model.OrigemMedicao;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.Transformador;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "scada.monitoramento.comunicacao.enabled=false",
        "scada.monitoramento.comunicacao.timeout-segundos=300",
        "monitoramento.comunicacao.timeout-segundos=999"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MonitoramentoE2ETest {
    private static final Instant INICIO = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired private EntityManager em;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void detectaPerdaAposLimiteERecuperaSomenteComNovaLeitura() throws Exception {
        ajustarRelogio(INICIO);
        var fonte = criarFonte();
        Long ativoId = fonte.getAtivo().getId();
        Long fonteId = fonte.getId();
        autenticar();
        var chave = UUID.randomUUID();
        var leitura = payload(fonteId, INICIO, "8");

        var original = ingerir(ativoId, chave, leitura).andExpect(status().isCreated())
                .andExpect(jsonPath("$.recebidoEm").value(INICIO.toString())).andReturn().getResponse();
        var medicaoOriginal = json.readTree(original.getContentAsString());
        verificarSnapshot(ativoId, fonteId, 80, INICIO);

        ajustarRelogio(INICIO.plusSeconds(300));
        var replay = ingerir(ativoId, chave, leitura).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(replay.getContentAsString())).isEqualTo(medicaoOriginal);
        avaliar();
        consultarAlertas(fonteId).andExpect(jsonPath("$.totalElements").value(0));

        Instant perda = INICIO.plusSeconds(301);
        ajustarRelogio(perda);
        avaliar();
        consultarAlertas(fonteId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ATIVO"))
                .andExpect(jsonPath("$.content[0].toleranciaSegundos").value(300))
                .andExpect(jsonPath("$.content[0].detectadoEm").value(perda.toString()));

        Instant recuperacao = perda.plusSeconds(10);
        ajustarRelogio(recuperacao);
        ingerir(ativoId, chave, leitura).andExpect(status().isOk())
                .andExpect(jsonPath("$.recebidoEm").value(INICIO.toString()));
        consultarAlertas(fonteId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ATIVO"));

        ingerir(ativoId, UUID.randomUUID(), payload(fonteId, recuperacao, "9"))
                .andExpect(status().isCreated());
        consultarAlertas(fonteId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("RESTABELECIDO"))
                .andExpect(jsonPath("$.content[0].restabelecidoEm").value(recuperacao.toString()));
        verificarSnapshot(ativoId, fonteId, 90, recuperacao);
        em.clear();
        assertThat(em.find(FonteMedicao.class, fonteId).getUltimaComunicacaoEm().toInstant())
                .isEqualTo(recuperacao);
    }

    private void ajustarRelogio(Instant instante) {
        when(clock.instant()).thenReturn(instante);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private void avaliar() throws Exception {
        mvc.perform(post("/api/jobs/avaliar-comunicacao").session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
    }

    private ResultActions consultarAlertas(Long fonteId) throws Exception {
        return mvc.perform(get("/api/alertas-comunicacao").session(sessao).param("fonteId", fonteId.toString()))
                .andExpect(status().isOk());
    }

    private void verificarSnapshot(Long ativoId, Long fonteId, int carga, Instant instante) throws Exception {
        mvc.perform(get("/api/monitoramento/snapshot").session(sessao).param("ativoId", ativoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cargaPercentual.valor").value((double) carga))
                .andExpect(jsonPath("$.content[0].cargaPercentual.fonteId").value(fonteId.intValue()))
                .andExpect(jsonPath("$.content[0].cargaPercentual.medidoEm").value(instante.toString()));
    }

    private ResultActions ingerir(Long ativoId, UUID chave, String leitura) throws Exception {
        return mvc.perform(post("/api/transformadores/{id}/medicoes", ativoId).session(sessao)
                .header(csrf.headerName(), csrf.token()).header("Idempotency-Key", chave.toString())
                .contentType(MediaType.APPLICATION_JSON).content(leitura));
    }

    private String payload(Long fonteId, Instant instante, String potencia) throws Exception {
        return json.writeValueAsString(Map.of("fonteId", fonteId, "medidoEm", instante.toString(),
                "potenciaAparenteMva", new BigDecimal(potencia)));
    }

    private void autenticar() throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = json.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
        csrf = json.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }

    private FonteMedicao criarFonte() {
        var subestacao = new Subestacao();
        subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(subestacao);
        var alimentador = new Alimentador();
        alimentador.setSubestacao(subestacao);
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(alimentador);
        var transformador = new Transformador();
        transformador.setNome("Transformador E2E");
        transformador.setAlimentador(alimentador);
        transformador.setCapacidadeMva(BigDecimal.TEN);
        transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(transformador);
        var fonte = new FonteMedicao();
        fonte.setAtivo(transformador);
        fonte.setIdentificador(UUID.randomUUID().toString());
        fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setMonitoradaDesde(INICIO.atOffset(ZoneOffset.UTC));
        em.persist(fonte);
        em.flush();
        return fonte;
    }
}
