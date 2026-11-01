package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;
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
class ManutencaoFluxoE2ETest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock clock;
    private static final Instant CADASTRO = Instant.parse("2026-11-01T12:00:00.123456Z");

    @Test
    void operadorDeveCadastrarIniciarConcluirEConsultarManutencaoComHistorico() throws Exception {
        when(clock.instant()).thenReturn(CADASTRO); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(transactionManager);
        Long ativoId = tx.execute(status -> {
            var ativo = new Subestacao(); ativo.setNome("Subestação do fluxo de manutenção");
            ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            ativo.setCapacidadeMva(new BigDecimal("100")); ativo.setTensaoNominalKv(new BigDecimal("138"));
            entityManager.persist(ativo); entityManager.flush(); return ativo.getId();
        });
        try {
            Long operadorId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, "operador@powergrid.local");
            var inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", "operador@powergrid.local")
                    .param("password", "SenhaSegura123")).andExpect(status().isOk());
            var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            var csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);

            var cadastro = Map.of("ativoId", ativoId, "categoria", "MANUTENCAO", "descricao", "Inspeção preventiva da subestação",
                    "responsavelUsuarioId", operadorId, "inicioPrevisto", CADASTRO.minusSeconds(600).toString(),
                    "terminoPrevisto", CADASTRO.plusSeconds(3600).toString());
            var criacao = mockMvc.perform(post("/api/eventos-operacionais").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cadastro)))
                    .andExpect(status().isCreated()).andReturn();
            var evento = objectMapper.readValue(criacao.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
            assertThat(evento.id()).isPositive(); assertThat(evento.categoria()).isEqualTo(Categoria.MANUTENCAO);
            assertThat(evento.situacao()).isEqualTo("ABERTO");
            assertThat(evento.registradorUsuarioId()).isEqualTo(operadorId); assertThat(evento.responsavelUsuarioId()).isEqualTo(operadorId);
            assertThat(evento.inicioEfetivo()).isNull(); assertThat(evento.terminoEfetivo()).isNull();
            assertThat(criacao.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/eventos-operacionais/" + evento.id());
            String consulta = "/api/ativos-rede/" + ativoId + "/manutencoes";
            mockMvc.perform(get(consulta).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].evento.id").value(evento.id()))
                    .andExpect(jsonPath("$.content[0].ultimaTransicao").isEmpty());

            String transicoes = "/api/eventos-operacionais/" + evento.id() + "/transicoes";
            Instant inicioEfetivo = CADASTRO.plusSeconds(60);
            when(clock.instant()).thenReturn(inicioEfetivo);
            var inicio = mockMvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                            "situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Inspeção iniciada"))))
                    .andExpect(status().isCreated()).andReturn();
            var hInicio = objectMapper.readValue(inicio.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
            assertThat(hInicio.situacaoAnterior()).isEqualTo(SituacaoEventoOperacional.ABERTO);
            assertThat(hInicio.situacaoNova()).isEqualTo(SituacaoEventoOperacional.EM_ANDAMENTO);
            assertThat(hInicio.usuarioId()).isEqualTo(operadorId); assertThat(hInicio.alteradoEm().toInstant()).isEqualTo(inicioEfetivo);
            assertThat(inicio.getResponse().getHeader("Location")).isEqualTo("http://localhost" + transicoes + "/" + hInicio.id());

            Instant terminoEfetivo = inicioEfetivo.plusSeconds(600);
            when(clock.instant()).thenReturn(terminoEfetivo);
            var conclusao = mockMvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                            "situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO", "comentario", "Inspeção concluída sem anomalias"))))
                    .andExpect(status().isCreated()).andReturn();
            var hConclusao = objectMapper.readValue(conclusao.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
            assertThat(hConclusao.situacaoAnterior()).isEqualTo(SituacaoEventoOperacional.EM_ANDAMENTO);
            assertThat(hConclusao.situacaoNova()).isEqualTo(SituacaoEventoOperacional.CONCLUIDO);
            assertThat(hConclusao.usuarioId()).isEqualTo(operadorId); assertThat(hConclusao.alteradoEm().toInstant()).isEqualTo(terminoEfetivo);
            assertThat(conclusao.getResponse().getHeader("Location")).isEqualTo("http://localhost" + transicoes + "/" + hConclusao.id());

            var historicoAtivo = mockMvc.perform(get(consulta).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].evento.id").value(evento.id()))
                    .andExpect(jsonPath("$.content[0].evento.categoria").value("MANUTENCAO"))
                    .andExpect(jsonPath("$.content[0].evento.situacao").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.content[0].evento.inicioEfetivo").isNotEmpty())
                    .andExpect(jsonPath("$.content[0].evento.terminoEfetivo").isNotEmpty())
                    .andExpect(jsonPath("$.content[0].ultimaTransicao.id").value(hConclusao.id()))
                    .andExpect(jsonPath("$.content[0].ultimaTransicao.situacaoNova").value("CONCLUIDO")).andReturn();
            var tipoPagina = objectMapper.getTypeFactory().constructParametricType(PageResponseDTO.class, ManutencaoHistoricoResponseDTO.class);
            PageResponseDTO<ManutencaoHistoricoResponseDTO> pagina = objectMapper.readValue(historicoAtivo.getResponse().getContentAsString(), tipoPagina);
            var item = pagina.content().getFirst();
            assertThat(item.evento().ativoId()).isEqualTo(ativoId);
            assertThat(item.evento().inicioEfetivo().toInstant()).isEqualTo(inicioEfetivo);
            assertThat(item.evento().terminoEfetivo().toInstant()).isEqualTo(terminoEfetivo);
            assertThat(item.evento().inicioEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(item.evento().terminoEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(item.ultimaTransicao()).isEqualTo(hConclusao);
            mockMvc.perform(get(transicoes).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(hConclusao.id()))
                    .andExpect(jsonPath("$.content[1].id").value(hInicio.id()));
            mockMvc.perform(get(criacao.getResponse().getHeader("Location")).session(sessao))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONCLUIDO"));
            assertThat(jdbc.queryForObject("SELECT estado_operacional FROM ativo_rede WHERE id = ?", String.class, ativoId))
                    .isEqualTo("OPERACIONAL");
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", ativoId);
                jdbc.update("DELETE FROM evento_operacional WHERE ativo_id = ?", ativoId);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ativoId); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ativoId);
            });
        }
    }
}
