package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TratativaAlertaFluxoE2ETest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void operadorDeveReconhecerEResolverSemPularEtapaOuDuplicarAuditoria() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Long alertaId = null;
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'OPERADOR'", String.class);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            var me = mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isOk()).andReturn();
            var usuario = mapper.readValue(me.getResponse().getContentAsString(), UsuarioAutenticadoResponse.class);
            var criado = mvc.perform(post("/api/alertas").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "tipo", "Ocorrência do fluxo de tratativa", "criticidade", "ALTA", "escopo", "REDE_MONITORADA"))))
                    .andExpect(status().isCreated()).andReturn();
            var inicialDto = dto(criado); alertaId = inicialDto.id();
            assertThat(inicialDto.situacao()).isEqualTo(br.com.powergrid.controlcenter.model.SituacaoAlerta.ABERTO);
            assertThat(inicialDto.geradoEm().toInstant()).isEqualTo(AGORA);
            assertThat(inicialDto.reconhecidoEm()).isNull(); assertThat(inicialDto.resolvidoEm()).isNull();

            when(clock.instant()).thenReturn(AGORA.plusSeconds(10));
            agir(alertaId, "resolucao", "Tentativa de pular etapa", 409);
            assertThat(jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, alertaId)).isEqualTo("ABERTO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, alertaId)).isZero();

            when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
            var reconhecido = dto(agir(alertaId, "reconhecimento", "Operador assumiu a tratativa", 200));
            assertThat(reconhecido.situacao()).isEqualTo(br.com.powergrid.controlcenter.model.SituacaoAlerta.RECONHECIDO);
            assertThat(reconhecido.reconhecidoPorUsuarioId()).isEqualTo(usuario.id());
            assertThat(reconhecido.reconhecidoEm().toInstant()).isEqualTo(AGORA.plusSeconds(60));
            var painelReconhecido = dashboard(alertaId, "RECONHECIDO");
            assertThat(painelReconhecido.reconhecidoPorUsuarioId()).isEqualTo(usuario.id());
            assertThat(painelReconhecido.reconhecidoEm()).isEqualTo(reconhecido.reconhecidoEm());
            assertThat(painelReconhecido.resolvidoEm()).isNull();

            when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
            var duploClique = dto(agir(alertaId, "reconhecimento", "Segundo clique", 200));
            assertThat(duploClique).isEqualTo(reconhecido);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, alertaId)).isEqualTo(1);

            when(clock.instant()).thenReturn(AGORA.plusSeconds(180));
            var resolvido = dto(agir(alertaId, "resolucao", "Ocorrência verificada e resolvida", 200));
            assertThat(resolvido.situacao()).isEqualTo(br.com.powergrid.controlcenter.model.SituacaoAlerta.RESOLVIDO);
            assertThat(resolvido.resolvidoPorUsuarioId()).isEqualTo(usuario.id());
            assertThat(resolvido.resolvidoEm().toInstant()).isEqualTo(AGORA.plusSeconds(180));
            assertThat(resolvido.reconhecidoEm()).isEqualTo(reconhecido.reconhecidoEm());
            var painelResolvido = dashboard(alertaId, "RESOLVIDO");
            assertThat(painelResolvido.resolvidoPorUsuarioId()).isEqualTo(usuario.id());
            assertThat(painelResolvido.resolvidoEm()).isEqualTo(resolvido.resolvidoEm());
            assertThat(painelResolvido.ocorridoEm()).isEqualTo(inicialDto.geradoEm());

            var consulta = mvc.perform(get("/api/alertas/{id}/historico", alertaId).session(sessao))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].situacaoAnterior").value("RECONHECIDO"))
                    .andExpect(jsonPath("$.content[0].situacaoNova").value("RESOLVIDO"))
                    .andExpect(jsonPath("$.content[1].situacaoAnterior").value("ABERTO"))
                    .andExpect(jsonPath("$.content[1].situacaoNova").value("RECONHECIDO")).andReturn();
            var lista = mapper.readTree(consulta.getResponse().getContentAsString()).get("content");
            var hResolucao = mapper.readValue(lista.get(0).toString(), HistoricoAlertaResponseDTO.class);
            var hReconhecimento = mapper.readValue(lista.get(1).toString(), HistoricoAlertaResponseDTO.class);
            assertThat(hResolucao.alertaId()).isEqualTo(alertaId); assertThat(hReconhecimento.alertaId()).isEqualTo(alertaId);
            assertThat(hResolucao.usuarioId()).isEqualTo(usuario.id()); assertThat(hReconhecimento.usuarioId()).isEqualTo(usuario.id());
            assertThat(hResolucao.alteradoEm()).isEqualTo(resolvido.resolvidoEm());
            assertThat(hReconhecimento.alteradoEm()).isEqualTo(reconhecido.reconhecidoEm());
            assertThat(hResolucao.comentario()).isEqualTo("Ocorrência verificada e resolvida");
            assertThat(hReconhecimento.comentario()).isEqualTo("Operador assumiu a tratativa");
            mvc.perform(get("/api/alertas/{id}/historico", alertaId).session(sessao).param("size", "1").param("page", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.last").value(true))
                    .andExpect(jsonPath("$.content[0].id").value(hReconhecimento.id()));
            mvc.perform(get("/api/alertas/{id}", alertaId).session(sessao)).andExpect(status().isOk())
                    .andExpect(content().json(mapper.writeValueAsString(resolvido)));
            mvc.perform(get("/api/alertas").session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + alertaId + ")]").isEmpty());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, alertaId)).isEqualTo(2);
        } finally {
            Long id = alertaId;
            if (id != null) new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM historico_alerta WHERE alerta_id = ?", id);
                jdbc.update("DELETE FROM alerta WHERE id = ?", id);
            });
        }
    }

    private MvcResult agir(Long id, String acao, String comentario, int esperado) throws Exception {
        return mvc.perform(patch("/api/alertas/" + id + "/" + acao).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("comentario", comentario))))
                .andExpect(status().is(esperado)).andReturn();
    }
    private AlertaResponseDTO dto(MvcResult result) throws Exception {
        return mapper.readValue(result.getResponse().getContentAsString(), AlertaResponseDTO.class);
    }
    private AlertaDashboardResponseDTO dashboard(Long id, String statusOrigem) throws Exception {
        var resposta = mvc.perform(get("/api/alertas-dashboard").session(sessao)
                .param("tipoAlerta", "GENERICO").param("statusOrigem", statusOrigem).param("size", "100")
                .param("dataInicio", AGORA.toString()).param("dataFim", AGORA.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andReturn();
        var conteudo = mapper.readTree(resposta.getResponse().getContentAsString()).get("content");
        var encontrados = new java.util.ArrayList<AlertaDashboardResponseDTO>();
        for (var item : conteudo) {
            var dto = mapper.readValue(item.toString(), AlertaDashboardResponseDTO.class);
            if (dto.idOrigem().equals(id)) encontrados.add(dto);
        }
        assertThat(encontrados).hasSize(1);
        return encontrados.getFirst();
    }
}
