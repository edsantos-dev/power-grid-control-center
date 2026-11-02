package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
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
class PerdaTecnicaFluxoE2ETest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock clock;

    @Test
    void gerenteDeveConfigurarIngerirAlterarParametroEConsultarEstimativaPonderadaECobertura() throws Exception {
        var agora = Instant.parse("2026-11-02T12:00:00Z");
        when(clock.instant()).thenReturn(agora); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        Long[] ids = tx.execute(status -> {
            var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
            var a = new Alimentador(); a.setSubestacao(sub); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(a); em.flush();
            return new Long[] {a.getId(), sub.getId()};
        });
        try {
            String email = jdbc.queryForObject("select email from usuario where perfil = 'GERENTE_OPERACOES'", String.class);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("email", email).param("password", "SenhaSegura123")).andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
            var csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            String base = "/api/alimentadores/" + ids[0];
            mvc.perform(patch(base + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"percentualPerdaTecnica\":3.5}")).andExpect(status().isOk());
            var fonteResult = mvc.perform(post(base + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("identificador", "PERDAS-E2E-" + ids[0], "origem", "SIMULADA"))))
                    .andExpect(status().isCreated()).andReturn();
            var fonte = mapper.readValue(fonteResult.getResponse().getContentAsString(), FonteAlimentadorResponseDTO.class);
            OffsetDateTime inicio = OffsetDateTime.parse("2026-11-01T00:00:00Z");
            long[] energia = {100, 200, 100};
            var medicaoIds = new java.util.ArrayList<Long>();
            for (int i = 0; i < 3; i++) {
                if (i == 2) mvc.perform(patch(base + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentualPerdaTecnica\":5.0}")).andExpect(status().isOk());
                var resultado = mvc.perform(post(base + "/fontes/" + fonte.id() + "/medicoes-energia").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("inicioPeriodo", inicio.plusHours(i).toString(),
                                "fimPeriodo", inicio.plusHours(i + 1).toString(), "energiaInjetadaMwh", energia[i]))))
                        .andExpect(status().isCreated()).andReturn();
                var m = mapper.readValue(resultado.getResponse().getContentAsString(), MedicaoEnergiaResponseDTO.class);
                assertThat(m.percentualPerdaReferencia()).isEqualByComparingTo(i == 2 ? "5.0" : "3.5"); medicaoIds.add(m.id());
            }
            var calculo = mvc.perform(get(base + "/perdas-tecnicas").session(sessao)
                    .param("dataInicio", inicio.toString()).param("dataFim", inicio.plusHours(3).toString()))
                    .andExpect(status().isOk()).andReturn();
            var dto = mapper.readValue(calculo.getResponse().getContentAsString(), PerdaTecnicaResponseDTO.class);
            assertThat(dto.situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
            assertThat(dto.totalEnergiaInjetadaMwh()).isEqualByComparingTo("400");
            assertThat(dto.totalPerdaEstimadaMwh()).isEqualByComparingTo("15.5");
            assertThat(dto.percentualPonderado()).isEqualByComparingTo("3.875000");
            assertThat(dto.percentualCobertura()).isEqualByComparingTo("100");
            assertThat(dto.medicoesConsideradas()).extracting(MedicaoEnergiaResponseDTO::id).containsExactlyElementsOf(medicaoIds);
            assertThat(dto.medicoesConsideradas().get(0).percentualPerdaReferencia()).isEqualByComparingTo("3.5");
            assertThat(dto.medicoesConsideradas().get(1).percentualPerdaReferencia()).isEqualByComparingTo("3.5");
            assertThat(dto.medicoesConsideradas().get(2).percentualPerdaReferencia()).isEqualByComparingTo("5");
            var lacunaResult = mvc.perform(get(base + "/perdas-tecnicas").session(sessao).param("dataInicio", inicio.toString())
                    .param("dataFim", inicio.plusHours(4).toString())).andExpect(status().isOk()).andReturn();
            var lacuna = mapper.readValue(lacunaResult.getResponse().getContentAsString(), PerdaTecnicaResponseDTO.class);
            assertThat(lacuna.situacao()).isEqualTo(SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
            assertThat(lacuna.percentualCobertura()).isEqualByComparingTo("75");
            assertThat(lacuna.totalEnergiaInjetadaMwh()).isNull(); assertThat(lacuna.totalPerdaEstimadaMwh()).isNull(); assertThat(lacuna.percentualPonderado()).isNull();
            mvc.perform(get(base + "/medicoes-energia").session(sessao).param("size", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content[0].id").value(medicaoIds.get(2))).andExpect(jsonPath("$.totalPages").value(2));
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("delete from medicao_energia_alimentador where fonte_id in (select id from fonte_medicao where ativo_id = ?)", ids[0]);
                jdbc.update("delete from fonte_medicao where ativo_id = ?", ids[0]);
                jdbc.update("delete from alimentador where id = ?", ids[0]); jdbc.update("delete from ativo_rede where id = ?", ids[0]);
                jdbc.update("delete from subestacao where id = ?", ids[1]); jdbc.update("delete from ativo_rede where id = ?", ids[1]);
            });
        }
    }
}
