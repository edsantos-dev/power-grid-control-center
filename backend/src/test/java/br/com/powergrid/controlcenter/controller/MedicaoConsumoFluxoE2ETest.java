package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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

@SpringBootTest(properties = "monitoramento.comunicacao.timeout-segundos=300")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class MedicaoConsumoFluxoE2ETest {
    private static final Instant PROVISIONAMENTO = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant RECEBIMENTO = PROVISIONAMENTO.plusSeconds(302);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void gerenteDeveCadastrarTopologiaIngerirConsumoEPreservarSnapshotsRestabelecendoComunicacao() throws Exception {
        when(clock.instant()).thenReturn(PROVISIONAMENTO); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Long[] ids = new Long[5];
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
                    "documento", "529.982.247-25"), ConsumidorDetalheResponseDTO.class);
            ids[3] = consumidor.id();
            var estoque = cadastrar("/api/medidores", Map.of("nome", "Medidor do consumo E2E", "numeroSerie", "CONSUMO-E2E-" + UUID.randomUUID()), MedidorResponseDTO.class);
            ids[4] = estoque.id();
            assertThat(estoque.consumidorId()).isNull(); assertThat(estoque.transformadorId()).isNull();
            String base = "/api/medidores/" + estoque.id();
            var instalacao = mvc.perform(patch(base + "/instalacao").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("consumidorId", consumidor.id(),
                            "transformadorId", transformador.id(), "latitude", "-23.5", "longitude", "-46.6", "dataInstalacao", "2026-01-01"))))
                    .andExpect(status().isOk()).andReturn();
            var instalado = mapper.readValue(instalacao.getResponse().getContentAsString(), MedidorResponseDTO.class);
            assertThat(instalado.alimentadorId()).isEqualTo(alimentador.id()); assertThat(instalado.subestacaoId()).isEqualTo(subestacao.id());
            var fonte = cadastrar(base + "/fontes", Map.of("origem", "SIMULADA"), FonteMedidorResponseDTO.class);
            assertThat(fonte.identificador()).startsWith("MEDIDOR-"); assertThat(fonte.medidorId()).isEqualTo(estoque.id());
            assertThat(fonte.monitoradaDesde().toInstant()).isEqualTo(PROVISIONAMENTO); assertThat(fonte.ultimaComunicacaoEm()).isNull();

            when(clock.instant()).thenReturn(PROVISIONAMENTO.plusSeconds(301));
            mvc.perform(post("/api/jobs/avaliar-comunicacao").session(sessao).header(csrf.headerName(), csrf.token())).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, fonte.id())).isEqualTo("ATIVO");
            when(clock.instant()).thenReturn(RECEBIMENTO);
            var criacao = mvc.perform(post(base + "/medicoes-consumo").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("fonteId", fonte.id(),
                            "inicioPeriodo", "2026-11-01T10:00:00Z", "fimPeriodo", "2026-11-01T11:00:00Z", "consumoKwh", new BigDecimal("150.5")))))
                    .andExpect(status().isCreated()).andReturn();
            var medicao = mapper.readValue(criacao.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
            String detalhe = base + "/medicoes-consumo/" + medicao.id();
            assertThat(criacao.getResponse().getHeader("Location")).isEqualTo("http://localhost" + detalhe);
            var consulta = mvc.perform(get(detalhe).session(sessao)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.consumoKwh").value(150.5))
                    .andExpect(jsonPath("$.consumidorId").value(consumidor.id()))
                    .andExpect(jsonPath("$.transformadorId").value(transformador.id()))
                    .andExpect(jsonPath("$.alimentadorId").value(alimentador.id()))
                    .andExpect(jsonPath("$.subestacaoId").value(subestacao.id())).andReturn();
            var dto = mapper.readValue(consulta.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
            assertThat(dto).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(medicao);
            assertThat(dto.consumoKwh()).isEqualByComparingTo("150.5");
            assertThat(dto.medidorId()).isEqualTo(estoque.id()); assertThat(dto.fonteId()).isEqualTo(fonte.id());
            assertThat(dto.recebidoEm().toInstant()).isEqualTo(RECEBIMENTO); assertThat(dto.recebidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(jdbc.queryForObject("SELECT consumidor_id FROM medicao_consumo_medidor WHERE id = ?", Long.class, dto.id())).isEqualTo(consumidor.id());
            assertThat(jdbc.queryForObject("SELECT transformador_id FROM medicao_consumo_medidor WHERE id = ?", Long.class, dto.id())).isEqualTo(transformador.id());
            assertThat(jdbc.queryForObject("SELECT alimentador_id FROM medicao_consumo_medidor WHERE id = ?", Long.class, dto.id())).isEqualTo(alimentador.id());
            assertThat(jdbc.queryForObject("SELECT subestacao_id FROM medicao_consumo_medidor WHERE id = ?", Long.class, dto.id())).isEqualTo(subestacao.id());
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, fonte.id()).toInstant())
                    .isEqualTo(RECEBIMENTO);
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, fonte.id())).isEqualTo("RESTABELECIDO");
            assertThat(jdbc.queryForObject("SELECT restabelecido_em FROM alerta_comunicacao WHERE fonte_id = ?", OffsetDateTime.class, fonte.id()).toInstant())
                    .isEqualTo(RECEBIMENTO);
        } finally {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                if (ids[4] != null) {
                    jdbc.update("DELETE FROM medicao_consumo_medidor WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", ids[4]);
                    jdbc.update("DELETE FROM alerta_comunicacao WHERE ativo_id = ?", ids[4]);
                    jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id = ?", ids[4]); removerAtivo("medidor_inteligente", ids[4]);
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
