package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import br.com.powergrid.controlcenter.model.SituacaoConsumoMedido;
import java.util.UUID;
import br.com.powergrid.controlcenter.dto.*;
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
class HistoricoConsumoMedidoFluxoE2ETest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void gerenteDeveConsultarTresDiasAnulandoPontoComLacunaDeUmDosMedidores() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Long[] ids = new Long[4];
        var medidores = new ArrayList<Long>();
        var fontes = new ArrayList<Long>();
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", String.class);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);

            var subestacao = cadastrar("/api/subestacoes", Map.of("nome", "Subestação do consumo E2E", "latitude", "-23.5",
                    "longitude", "-46.6", "tensaoNominalKv", "138", "capacidadeMva", "100", "dataInstalacao", "2025-01-01"), SubestacaoResponseDTO.class);
            ids[0] = subestacao.id();
            var alimentador = cadastrar("/api/alimentadores", Map.of("nome", "Alimentador do consumo E2E", "latitude", "-23.5",
                    "longitude", "-46.6", "tensaoNominalKv", "13.8", "subestacaoId", subestacao.id(), "dataInstalacao", "2025-01-01"), AlimentadorResponseDTO.class);
            ids[1] = alimentador.id();
            var transformador = cadastrar("/api/transformadores", Map.of("nome", "Transformador do consumo E2E", "latitude", "-23.5",
                    "longitude", "-46.6", "capacidadeMva", "10", "alimentadorId", alimentador.id(), "dataInstalacao", "2025-01-01"), TransformadorResponseDTO.class);
            ids[2] = transformador.id();
            var consumidor = cadastrar("/api/consumidores", Map.of("nomeRazaoSocial", "Titular do consumo E2E", "tipoPessoa", "PF",
                    "documento", "529.982.247-25"), ConsumidorResponseDTO.class);
            ids[3] = consumidor.id();
            for (String nome : List.of("A", "B")) {
                var estoque = cadastrar("/api/medidores", Map.of("nome", "Medidor histórico " + nome,
                        "numeroSerie", "HISTORICO-CONSUMO-E2E-" + UUID.randomUUID()), MedidorResponseDTO.class);
                medidores.add(estoque.id());
                String base = "/api/medidores/" + estoque.id();
                mvc.perform(patch(base + "/instalacao").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "consumidorId", consumidor.id(), "transformadorId", transformador.id(),
                                "latitude", "-23.5", "longitude", "-46.6", "dataInstalacao", "2026-01-01"))))
                        .andExpect(status().isOk());
                var fonte = cadastrar(base + "/fontes", Map.of("origem", "SIMULADA"), FonteMedidorResponseDTO.class);
                fontes.add(fonte.id());
                assertThat(fonte.medidorId()).isEqualTo(estoque.id());
            }

            int[][] consumos = {{100, 50}, {120, 80}, {90}};
            for (int dia = 0; dia < consumos.length; dia++) {
                for (int equipamento = 0; equipamento < consumos[dia].length; equipamento++) {
                    var leitura = cadastrar("/api/medidores/" + medidores.get(equipamento) + "/medicoes-consumo",
                            Map.of("fonteId", fontes.get(equipamento), "inicioPeriodo", INICIO.plusDays(dia).toString(),
                                    "fimPeriodo", INICIO.plusDays(dia + 1).toString(), "consumoKwh", consumos[dia][equipamento]),
                            MedicaoConsumoResponseDTO.class);
                    assertThat(leitura.consumidorId()).isEqualTo(consumidor.id());
                    assertThat(leitura.transformadorId()).isEqualTo(transformador.id());
                    assertThat(leitura.alimentadorId()).isEqualTo(alimentador.id());
                    assertThat(leitura.subestacaoId()).isEqualTo(subestacao.id());
                }
            }

            var consulta = mvc.perform(get("/api/alimentadores/{id}/historico-consumo-medido", alimentador.id()).session(sessao)
                    .param("dataInicio", INICIO.toString()).param("dataFim", INICIO.plusDays(3).toString())
                    .param("granularidade", "DIA"))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.possuiDados").value(true))
                    .andExpect(jsonPath("$.quantidadeMedidoresConsiderados").value(2))
                    .andExpect(jsonPath("$.coberturaCadastral").value("NAO_AFERIDA"))
                    .andExpect(jsonPath("$.pontos.length()").value(3))
                    .andExpect(jsonPath("$.pontos[0].situacao").value("OK"))
                    .andExpect(jsonPath("$.pontos[0].consumoMedidoMwh").value(0.15))
                    .andExpect(jsonPath("$.pontos[1].situacao").value("OK"))
                    .andExpect(jsonPath("$.pontos[1].consumoMedidoMwh").value(0.2))
                    .andExpect(jsonPath("$.pontos[2].situacao").value("DADOS_INSUFICIENTES"))
                    .andExpect(jsonPath("$.pontos[2].consumoMedidoMwh").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.pontos[2].quantidadeMedidoresComCoberturaCompleta").value(1))
                    .andReturn();
            var dto = mapper.readValue(consulta.getResponse().getContentAsString(), HistoricoConsumoMedidoResponseDTO.class);
            assertThat(dto.alimentadorId()).isEqualTo(alimentador.id());
            assertThat(dto.fusoHorario()).isEqualTo("UTC");
            assertThat(dto.granularidade()).isEqualTo(br.com.powergrid.controlcenter.model.GranularidadeHistorico.DIA);
            assertThat(dto.dataInicio()).isEqualTo(INICIO);
            assertThat(dto.dataFim()).isEqualTo(INICIO.plusDays(3));
            for (int dia = 0; dia < 3; dia++) {
                var ponto = dto.pontos().get(dia);
                assertThat(ponto.inicioPeriodo()).isEqualTo(INICIO.plusDays(dia));
                assertThat(ponto.fimPeriodo()).isEqualTo(INICIO.plusDays(dia + 1));
                assertThat(ponto.quantidadeLeituras()).isEqualTo(dia < 2 ? 2 : 1);
                assertThat(ponto.quantidadeMedidoresComCoberturaCompleta()).isEqualTo(dia < 2 ? 2 : 1);
                if (dia < 2) {
                    assertThat(ponto.situacao()).isEqualTo(SituacaoConsumoMedido.OK);
                    assertThat(ponto.consumoMedidoMwh()).isEqualByComparingTo(dia == 0 ? "0.150000000" : "0.200000000");
                    assertThat(ponto.consumoMedidoMwh().scale()).isEqualTo(9);
                } else {
                    assertThat(ponto.situacao()).isEqualTo(SituacaoConsumoMedido.DADOS_INSUFICIENTES);
                    assertThat(ponto.consumoMedidoMwh()).isNull();
                }
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medicao_consumo_medidor WHERE alimentador_id = ?", Long.class,
                    alimentador.id())).isEqualTo(5);
        } finally {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                for (Long medidor : medidores) {
                    jdbc.update("DELETE FROM medicao_consumo_medidor WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", medidor);
                    jdbc.update("DELETE FROM alerta_comunicacao WHERE ativo_id = ?", medidor);
                    jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id = ?", medidor);
                    removerAtivo("medidor_inteligente", medidor);
                }
                if (ids[3] != null) jdbc.update("DELETE FROM consumidor WHERE id = ?", ids[3]);
                removerAtivo("transformador", ids[2]); removerAtivo("alimentador", ids[1]); removerAtivo("subestacao", ids[0]);
            });
        }
    }

    private <T> T cadastrar(String uri, Map<String, ?> corpo, Class<T> tipo) throws Exception {
        var resultado = mvc.perform(post(uri).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().isCreated()).andReturn();
        return mapper.readValue(resultado.getResponse().getContentAsString(), tipo);
    }

    private void removerAtivo(String tabela, Long id) {
        if (id == null) return;
        jdbc.update("DELETE FROM " + tabela + " WHERE id = ?", id); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
    }
}
