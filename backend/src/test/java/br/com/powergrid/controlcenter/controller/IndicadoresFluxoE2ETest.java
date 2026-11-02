package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.SituacaoDisponibilidade;
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
class IndicadoresFluxoE2ETest {
    private static final Instant CADASTRO = Instant.parse("2026-11-02T09:00:00Z");
    private static final Instant INICIO_JANELA = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant FIM_JANELA = Instant.parse("2026-11-01T18:00:00Z");
    private static final Instant INICIO_FISICO = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant TERMINO_FISICO = Instant.parse("2026-11-01T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock clock;

    @Test
    void gerenteDeveCadastrarTransformadorRestabelecerInterrupcaoEConsultarDisponibilidadeDeOitentaPorCento() throws Exception {
        when(clock.instant()).thenReturn(CADASTRO); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        var topologia = tx.execute(status -> {
            var subestacao = new Subestacao(); subestacao.setNome("Subestação do E2E de indicadores");
            subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(subestacao);
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador do E2E de indicadores");
            alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); alimentador.setSubestacao(subestacao);
            em.persist(alimentador); em.flush(); return new Topologia(subestacao.getId(), alimentador.getId());
        });
        Long transformadorId = null;
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", String.class);
            Long gerenteId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            var csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);

            var cadastroTransformador = mvc.perform(post("/api/transformadores").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "nome", "Transformador do E2E de indicadores", "latitude", -23, "longitude", -46,
                            "capacidadeMva", 100, "alimentadorId", topologia.alimentadorId(), "dataInstalacao", "2026-10-01"))))
                    .andExpect(status().isCreated()).andReturn();
            var transformador = mapper.readValue(cadastroTransformador.getResponse().getContentAsString(), TransformadorResponseDTO.class);
            transformadorId = transformador.id();
            assertThat(transformador.subestacaoId()).isEqualTo(topologia.subestacaoId());

            var cadastroInterrupcao = mvc.perform(post("/api/interrupcoes").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "ativoId", transformadorId, "descricao", "Interrupção física de duas horas",
                            "responsavelUsuarioId", gerenteId, "inicioEfetivo", INICIO_FISICO.toString()))))
                    .andExpect(status().isCreated()).andReturn();
            var evento = mapper.readValue(cadastroInterrupcao.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
            assertThat(evento.situacao()).isEqualTo("ABERTO");
            assertThat(evento.registradorUsuarioId()).isEqualTo(gerenteId);
            assertThat(evento.inicioEfetivo().toInstant()).isEqualTo(INICIO_FISICO);

            String transicoes = "/api/eventos-operacionais/" + evento.id() + "/transicoes";
            when(clock.instant()).thenReturn(CADASTRO.plusSeconds(60));
            mvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO",
                            "comentario", "Atendimento registrado")))).andExpect(status().isCreated());
            Instant registroConclusao = CADASTRO.plusSeconds(120);
            when(clock.instant()).thenReturn(registroConclusao);
            var conclusao = mvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO",
                            "comentario", "Energia restabelecida às 12h", "terminoEfetivo", TERMINO_FISICO.toString()))))
                    .andExpect(status().isCreated()).andReturn();
            var historico = mapper.readValue(conclusao.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
            assertThat(historico.situacaoNova()).isEqualTo(SituacaoEventoOperacional.CONCLUIDO);
            assertThat(historico.alteradoEm().toInstant()).isEqualTo(registroConclusao).isAfter(FIM_JANELA);

            var consulta = mvc.perform(get("/api/indicadores/operacionais").session(sessao)
                    .param("dataInicio", INICIO_JANELA.toString()).param("dataFim", FIM_JANELA.toString())
                    .param("ativoId", transformadorId.toString()).param("tipos", "TRANSFORMADOR")
                    .param("subestacaoId", topologia.subestacaoId().toString()))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.totalAtivos").value(1))
                    .andExpect(jsonPath("$.tempoInterrompidoSegundos").value(7200.0))
                    .andExpect(jsonPath("$.tempoMedioRestabelecimentoSegundos").value(7200.0))
                    .andExpect(jsonPath("$.tempoObservadoAtivosSegundos").value(36000.0))
                    .andExpect(jsonPath("$.disponibilidadeEstimadaPercentual").value(80.0))
                    .andExpect(jsonPath("$.situacaoDisponibilidade").value("COBERTURA_NAO_AFERIDA")).andReturn();
            var indicadores = mapper.readValue(consulta.getResponse().getContentAsString(), IndicadoresOperacionaisResponseDTO.class);
            assertThat(indicadores.tempoInterrompidoSegundos()).isEqualByComparingTo("7200.000000");
            assertThat(indicadores.tempoMedioRestabelecimentoSegundos()).isEqualByComparingTo("7200.000000");
            assertThat(indicadores.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("80.000000");
            assertThat(indicadores.disponibilidadeEstimadaPercentual().scale()).isEqualTo(6);
            assertThat(indicadores.situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.COBERTURA_NAO_AFERIDA);
            assertThat(indicadores.quantidadeIniciadasNoPeriodo()).isEqualTo(1);
            assertThat(indicadores.quantidadeRestabelecimentosNoPeriodo()).isEqualTo(1);
            assertThat(indicadores.quantidadeSemTerminoFisico()).isZero();
            assertThat(indicadores.quantidadeRegistrosInconsistentes()).isZero();
            assertThat(indicadores.calculadoEm().toInstant()).isEqualTo(registroConclusao);
            mvc.perform(get("/api/interrupcoes/{id}", evento.id()).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.situacao").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.inicioEfetivo").value("2026-11-01T10:00:00Z"))
                    .andExpect(jsonPath("$.terminoEfetivo").value("2026-11-01T12:00:00Z"));
        } finally {
            Long ativoId = transformadorId;
            tx.executeWithoutResult(status -> {
                if (ativoId != null) {
                    jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", ativoId);
                    jdbc.update("DELETE FROM evento_operacional WHERE ativo_id = ?", ativoId);
                    jdbc.update("DELETE FROM transformador WHERE id = ?", ativoId);
                    jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ativoId);
                }
                jdbc.update("DELETE FROM alimentador WHERE id = ?", topologia.alimentadorId());
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", topologia.alimentadorId());
                jdbc.update("DELETE FROM subestacao WHERE id = ?", topologia.subestacaoId());
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", topologia.subestacaoId());
            });
        }
    }

    private record Topologia(Long subestacaoId, Long alimentadorId) { }
}
