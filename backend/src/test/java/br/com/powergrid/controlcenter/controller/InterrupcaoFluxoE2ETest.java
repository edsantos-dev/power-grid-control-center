package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
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
class InterrupcaoFluxoE2ETest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Clock clock;
    private static final Instant CADASTRO = Instant.parse("2026-11-02T12:00:00.123456Z");

    @Test
    void operadorDeveRegistrarAtenderRestabelecerEConsultarComTemposFisicosEAuditoriaDistintos() throws Exception {
        when(clock.instant()).thenReturn(CADASTRO); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        Long ativoId = tx.execute(status -> {
            var ativo = new Subestacao(); ativo.setNome("Ativo do fluxo E2E de interrupção");
            ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(ativo); em.flush(); return ativo.getId();
        });
        try {
            Long operador = jdbc.queryForObject("select id from usuario where email = ?", Long.class, "operador@powergrid.local");
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", "operador@powergrid.local")
                    .param("password", "SenhaSegura123")).andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            var csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            Instant inicioFisico = CADASTRO.minusSeconds(3600);
            var criacao = mvc.perform(post("/api/interrupcoes").session(sessao).header(csrf.headerName(), csrf.token())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                            "ativoId", ativoId, "descricao", "Fornecimento interrompido", "responsavelUsuarioId", operador,
                            "inicioEfetivo", inicioFisico.atOffset(ZoneOffset.ofHours(-3)).toString()))))
                    .andExpect(status().isCreated()).andReturn();
            var evento = mapper.readValue(criacao.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
            assertThat(evento.id()).isPositive(); assertThat(evento.categoria()).isEqualTo(Categoria.INTERRUPCAO_FORNECIMENTO);
            assertThat(evento.situacao()).isEqualTo("ABERTO"); assertThat(evento.inicioEfetivo().toInstant()).isEqualTo(inicioFisico);
            assertThat(evento.registradorUsuarioId()).isEqualTo(operador); assertThat(evento.responsavelUsuarioId()).isEqualTo(operador);
            assertThat(evento.terminoEfetivo()).isNull(); assertThat(evento.inicioPrevisto()).isNull();
            assertThat(criacao.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/eventos-operacionais/" + evento.id());

            String transicoes = "/api/eventos-operacionais/" + evento.id() + "/transicoes";
            Instant atendimento = CADASTRO.plusSeconds(300);
            when(clock.instant()).thenReturn(atendimento);
            var inicio = mvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Atendimento iniciado"))))
                    .andExpect(status().isCreated()).andReturn();
            var hInicio = mapper.readValue(inicio.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
            assertThat(hInicio.alteradoEm().toInstant()).isEqualTo(atendimento);
            assertThat(hInicio.situacaoNova()).isEqualTo(SituacaoEventoOperacional.EM_ANDAMENTO);

            Instant restabelecimento = CADASTRO.plusSeconds(1200);
            Instant registroConclusao = CADASTRO.plusSeconds(1800);
            when(clock.instant()).thenReturn(registroConclusao);
            var conclusao = mvc.perform(post(transicoes).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO",
                            "comentario", "Restabelecimento confirmado", "terminoEfetivo", restabelecimento.atOffset(ZoneOffset.ofHours(-3)).toString()))))
                    .andExpect(status().isCreated()).andReturn();
            var hConclusao = mapper.readValue(conclusao.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
            assertThat(hConclusao.alteradoEm().toInstant()).isEqualTo(registroConclusao).isNotEqualTo(restabelecimento);
            assertThat(hConclusao.usuarioId()).isEqualTo(operador);
            assertThat(hConclusao.situacaoNova()).isEqualTo(SituacaoEventoOperacional.CONCLUIDO);

            var detalhe = mvc.perform(get("/api/interrupcoes/{id}", evento.id()).session(sessao)).andExpect(status().isOk()).andReturn();
            var dto = mapper.readValue(detalhe.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
            assertThat(dto.situacao()).isEqualTo("CONCLUIDO"); assertThat(dto.ativoId()).isEqualTo(ativoId);
            assertThat(dto.inicioEfetivo().toInstant()).isEqualTo(inicioFisico).isNotEqualTo(atendimento);
            assertThat(dto.terminoEfetivo().toInstant()).isEqualTo(restabelecimento).isNotEqualTo(registroConclusao);
            assertThat(dto.inicioEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(dto.terminoEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(Duration.between(dto.inicioEfetivo(), dto.terminoEfetivo()).getSeconds()).isEqualTo(4800);
            var consulta = mvc.perform(get("/api/interrupcoes").session(sessao).param("ativoId", ativoId.toString()).param("situacao", "CONCLUIDO")
                    .param("dataInicio", inicioFisico.toString()).param("dataFim", inicioFisico.plusSeconds(1).toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(evento.id())).andReturn();
            var tipoPagina = mapper.getTypeFactory().constructParametricType(PageResponseDTO.class, EventoOperacionalResponseDTO.class);
            PageResponseDTO<EventoOperacionalResponseDTO> pagina = mapper.readValue(consulta.getResponse().getContentAsString(), tipoPagina);
            assertThat(pagina.content()).containsExactly(dto);
            var historico = mvc.perform(get(transicoes).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2)).andReturn();
            var tipoHistorico = mapper.getTypeFactory().constructParametricType(PageResponseDTO.class, HistoricoEventoResponseDTO.class);
            PageResponseDTO<HistoricoEventoResponseDTO> trilha = mapper.readValue(historico.getResponse().getContentAsString(), tipoHistorico);
            assertThat(trilha.content()).containsExactly(hConclusao, hInicio);
            assertThat(hInicio.usuarioId()).isEqualTo(operador);
            assertThat(jdbc.queryForObject("select estado_operacional from ativo_rede where id = ?", String.class, ativoId)).isEqualTo("OPERACIONAL");
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("delete from historico_evento_operacional where evento_id in (select id from evento_operacional where ativo_id = ?)", ativoId);
                jdbc.update("delete from evento_operacional where ativo_id = ?", ativoId);
                jdbc.update("delete from subestacao where id = ?", ativoId); jdbc.update("delete from ativo_rede where id = ?", ativoId);
            });
        }
    }
}
