package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.SituacaoPerdaTecnica;
import br.com.powergrid.controlcenter.model.GranularidadeHistorico;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class HistoricoEnergiaFluxoE2ETest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock clock;

    @Test
    void gerenteDeveCadastrarIngerirEConsultarCincoDiasComPerdasPonderadasELacunasNulas() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T12:00:00Z")); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        Long subestacaoId = tx.execute(status -> {
            var sub = new Subestacao(); sub.setNome("Subestação do histórico E2E");
            sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub); em.flush(); return sub.getId();
        });
        Long alimentadorId = null;
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", String.class);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            var csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            var cadastro = mvc.perform(post("/api/alimentadores").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "nome", "Alimentador do histórico E2E", "latitude", -23, "longitude", -46,
                            "tensaoNominalKv", 13.8, "subestacaoId", subestacaoId, "dataInstalacao", "2026-09-01"))))
                    .andExpect(status().isCreated()).andReturn();
            var alimentador = mapper.readValue(cadastro.getResponse().getContentAsString(), AlimentadorResponseDTO.class);
            alimentadorId = alimentador.id(); String base = "/api/alimentadores/" + alimentadorId;
            var cadastroFonte = mvc.perform(post(base + "/fontes").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "identificador", "HISTORICO-E2E-" + alimentadorId, "origem", "SIMULADA"))))
                    .andExpect(status().isCreated()).andReturn();
            var fonte = mapper.readValue(cadastroFonte.getResponse().getContentAsString(), FonteAlimentadorResponseDTO.class);
            int[] inicios = {0, 12, 24, 72}; int[] duracoes = {12, 12, 24, 24};
            int[] energias = {40, 60, 50, 200}; String[] percentuais = {"3.5", "5", "5", "2"};
            for (int i = 0; i < inicios.length; i++) {
                mvc.perform(patch(base + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentualPerdaTecnica\":" + percentuais[i] + "}"))
                        .andExpect(status().isOk());
                var leitura = mvc.perform(post(base + "/fontes/" + fonte.id() + "/medicoes-energia").session(sessao)
                        .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("inicioPeriodo", INICIO.plusHours(inicios[i]).toString(),
                                "fimPeriodo", INICIO.plusHours(inicios[i] + duracoes[i]).toString(), "energiaInjetadaMwh", energias[i]))))
                        .andExpect(status().isCreated()).andReturn();
                var medicao = mapper.readValue(leitura.getResponse().getContentAsString(), MedicaoEnergiaResponseDTO.class);
                assertThat(medicao.percentualPerdaReferencia()).isEqualByComparingTo(percentuais[i]);
            }
            mvc.perform(patch(base + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"percentualPerdaTecnica\":9}"))
                    .andExpect(status().isOk());

            var consulta = mvc.perform(get(base + "/historico-energia").session(sessao).param("dataInicio", INICIO.toString())
                    .param("dataFim", INICIO.plusDays(5).toString()).param("granularidade", "DIA"))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.fusoHorario").value("UTC")).andExpect(jsonPath("$.possuiDados").value(true))
                    .andExpect(jsonPath("$.pontos.length()").value(5))
                    .andExpect(jsonPath("$.pontos[0].situacao").value("OK"))
                    .andExpect(jsonPath("$.pontos[0].energiaInjetadaMwh").value(100.0))
                    .andExpect(jsonPath("$.pontos[0].perdaEstimadaMwh").value(4.4))
                    .andExpect(jsonPath("$.pontos[1].situacao").value("OK"))
                    .andExpect(jsonPath("$.pontos[1].energiaInjetadaMwh").value(50.0))
                    .andExpect(jsonPath("$.pontos[2].situacao").value("DADOS_INSUFICIENTES"))
                    .andExpect(jsonPath("$.pontos[2].energiaInjetadaMwh").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.pontos[2].perdaEstimadaMwh").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.pontos[3].situacao").value("OK"))
                    .andExpect(jsonPath("$.pontos[4].situacao").value("DADOS_INSUFICIENTES")).andReturn();
            var dto = mapper.readValue(consulta.getResponse().getContentAsString(), HistoricoEnergiaResponseDTO.class);
            assertThat(dto.alimentadorId()).isEqualTo(alimentadorId); assertThat(dto.granularidade()).isEqualTo(GranularidadeHistorico.DIA);
            assertThat(dto.dataInicio()).isEqualTo(INICIO); assertThat(dto.dataFim()).isEqualTo(INICIO.plusDays(5));
            assertThat(dto.pontos()).hasSize(5);
            validarCompleto(dto.pontos().get(0), "100", "4.4", "4.4", 2);
            validarCompleto(dto.pontos().get(1), "50", "2.5", "5", 1);
            validarCompleto(dto.pontos().get(3), "200", "4", "2", 1);
            for (int i = 0; i < 5; i++) {
                var p = dto.pontos().get(i);
                assertThat(p.inicioPeriodo()).isEqualTo(INICIO.plusDays(i));
                assertThat(p.fimPeriodo()).isEqualTo(INICIO.plusDays(i + 1));
                if (i == 2 || i == 4) {
                    assertThat(p.situacao()).isEqualTo(SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
                    assertThat(p.energiaInjetadaMwh()).isNull(); assertThat(p.perdaEstimadaMwh()).isNull();
                    assertThat(p.percentualPerdaPonderado()).isNull(); assertThat(p.quantidadeLeituras()).isZero();
                }
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medicao_energia_alimentador WHERE fonte_id = ?", Long.class, fonte.id()))
                    .isEqualTo(4);
        } finally {
            Long id = alimentadorId;
            tx.executeWithoutResult(status -> {
                if (id != null) {
                    jdbc.update("DELETE FROM medicao_energia_alimentador WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", id);
                    jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id = ?", id);
                    jdbc.update("DELETE FROM alimentador WHERE id = ?", id); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
                }
                jdbc.update("DELETE FROM subestacao WHERE id = ?", subestacaoId); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", subestacaoId);
            });
        }
    }

    private void validarCompleto(PontoHistoricoDTO ponto, String energia, String perda, String percentual, long leituras) {
        assertThat(ponto.situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
        assertThat(ponto.energiaInjetadaMwh()).isEqualByComparingTo(energia);
        assertThat(ponto.perdaEstimadaMwh()).isEqualByComparingTo(perda);
        assertThat(ponto.percentualPerdaPonderado()).isEqualByComparingTo(percentual);
        assertThat(ponto.quantidadeLeituras()).isEqualTo(leituras);
    }
}
