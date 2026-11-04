package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
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
class AlertaAtivoFluxoE2ETest {
    private static final Instant AGORA = Instant.parse("2026-11-04T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private DataSource dataSource;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void devePriorizarQuatroOrigensERetirarSomenteAsTratadasSemNMaisUm() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var topologia = criarTopologia();
        Long genericoId = null;
        try {
            autenticar();
            String sufixo = UUID.randomUUID().toString();
            long fonteLinha = fonte("/api/linhas-transmissao/" + topologia.linhaId() + "/fontes", "E2E-L-" + sufixo);
            long fonteTrafo = fonte("/api/transformadores/" + topologia.transformadorId() + "/fontes", "E2E-T-" + sufixo);
            long fonteSemContato = fonte("/api/transformadores/" + topologia.transformadorId() + "/fontes", "E2E-C-" + sufixo);
            enviar("/api/linhas-transmissao/" + topologia.linhaId() + "/medicoes", Map.of(
                    "fonteId", fonteLinha, "medidoEm", AGORA.minusSeconds(1).toString(), "tensaoKv", 90), 201);
            enviar("/api/transformadores/" + topologia.transformadorId() + "/medicoes", Map.of(
                    "fonteId", fonteTrafo, "medidoEm", AGORA.minusSeconds(1).toString(), "potenciaAparenteMva", 12), 201);
            var generico = enviar("/api/alertas", Map.of("tipo", "Ocorrência do despacho integrado",
                    "criticidade", "ALTA", "escopo", "ATIVO", "ativoId", topologia.transformadorId()), 201);
            genericoId = mapper.readValue(generico.getResponse().getContentAsString(), AlertaResponseDTO.class).id();
            new TransactionTemplate(manager).executeWithoutResult(status ->
                    jdbc.update("UPDATE fonte_medicao SET monitorada_desde = ? WHERE id = ?", AGORA.minusSeconds(301).atOffset(ZoneOffset.UTC), fonteSemContato));
            enviar("/api/jobs/avaliar-comunicacao", null, 200);
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.update("UPDATE alerta_sobrecarga SET criticidade = 'CRITICA' WHERE ativo_id = ?", topologia.transformadorId());
                jdbc.update("UPDATE alerta_tensao SET criticidade = 'MEDIA' WHERE ativo_id = ?", topologia.linhaId());
                jdbc.update("UPDATE alerta_comunicacao SET criticidade = 'BAIXA' WHERE fonte_id = ?", fonteSemContato);
            });

            var fila = consultarMedindo(null);
            assertThat(fila.totalElements()).isEqualTo(4);
            assertThat(fila.content()).extracting(AlertaAtivoResponseDTO::tipoAlerta)
                    .containsExactly(TipoAlertaDashboard.SOBRECARGA, TipoAlertaDashboard.GENERICO, TipoAlertaDashboard.TENSAO, TipoAlertaDashboard.COMUNICACAO);
            assertThat(fila.content()).extracting(AlertaAtivoResponseDTO::criticidade)
                    .containsExactly(Criticidade.CRITICA, Criticidade.ALTA, Criticidade.MEDIA, Criticidade.BAIXA);
            assertThat(fila.content()).extracting(AlertaAtivoResponseDTO::tipoAtivo)
                    .containsExactly(TipoAtivoAlerta.TRANSFORMADOR, TipoAtivoAlerta.TRANSFORMADOR, TipoAtivoAlerta.LINHA, TipoAtivoAlerta.TRANSFORMADOR);
            assertThat(consultarMedindo(topologia.origemId()).content()).isEqualTo(fila.content());
            var outraExtremidade = consultarMedindo(topologia.destinoId());
            assertThat(outraExtremidade.totalElements()).isEqualTo(1);
            assertThat(outraExtremidade.content().getFirst().tipoAlerta()).isEqualTo(TipoAlertaDashboard.TENSAO);

            when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
            long sobrecargaId = fila.content().getFirst().idOrigem();
            agir("/api/alertas-sobrecarga/" + sobrecargaId + "/reconhecimento", null);
            agir("/api/alertas/" + genericoId + "/reconhecimento", Map.of("comentario", "Operador assumiu a ocorrência"));
            var reconhecido = consultarMedindo(null);
            assertThat(reconhecido.totalElements()).isEqualTo(3);
            assertThat(reconhecido.content().getFirst().tipoAlerta()).isEqualTo(TipoAlertaDashboard.GENERICO);
            assertThat(reconhecido.content().getFirst().statusOrigem()).isEqualTo("RECONHECIDO");
            assertThat(reconhecido.content().getFirst().reconhecidoPorUsuarioId()).isNotNull();
            when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
            agir("/api/alertas/" + genericoId + "/resolucao", Map.of("comentario", "Ocorrência resolvida"));

            var restantes = consultarMedindo(null);
            assertThat(restantes.totalElements()).isEqualTo(2);
            assertThat(restantes.content()).extracting(AlertaAtivoResponseDTO::tipoAlerta)
                    .containsExactly(TipoAlertaDashboard.TENSAO, TipoAlertaDashboard.COMUNICACAO);
            assertThat(restantes.content()).allSatisfy(a -> assertThat(a.statusOrigem()).isEqualTo("ATIVO"));
            assertThat(restantes.content()).containsExactlyElementsOf(fila.content().subList(2, 4));
            assertThat(consultarMedindo(topologia.origemId()).content()).isEqualTo(restantes.content());
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_sobrecarga WHERE id = ?", String.class, sobrecargaId)).isEqualTo("RECONHECIDO");
            assertThat(jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, genericoId)).isEqualTo("RESOLVIDO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, genericoId)).isEqualTo(2);
            mvc.perform(get("/api/alertas-dashboard").session(sessao).param("ativoId", topologia.transformadorId().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        } finally { limpar(topologia, genericoId); }
    }

    private PageResponseDTO<AlertaAtivoResponseDTO> consultarMedindo(Long subestacaoId) throws Exception {
        var estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        boolean habilitadas = estatisticas.isStatisticsEnabled();
        var pool = dataSource.unwrap(HikariDataSource.class);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        estatisticas.setStatisticsEnabled(true);
        try {
            long comandos = estatisticas.getPrepareStatementCount();
            long buscas = estatisticas.getEntityFetchCount();
            long conexoes = estatisticas.getConnectCount();
            var request = get("/api/alertas/ativos").session(sessao);
            if (subestacaoId != null) request.param("subestacaoId", subestacaoId.toString());
            var resposta = mvc.perform(request).andExpect(status().isOk()).andReturn();
            var tipo = mapper.getTypeFactory().constructParametricType(PageResponseDTO.class, AlertaAtivoResponseDTO.class);
            PageResponseDTO<AlertaAtivoResponseDTO> pagina = mapper.readValue(resposta.getResponse().getContentAsString(), tipo);
            assertThat(estatisticas.getPrepareStatementCount() - comandos).isEqualTo(2);
            assertThat(estatisticas.getEntityFetchCount() - buscas).isZero();
            assertThat(estatisticas.getConnectCount() - conexoes).isEqualTo(1);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isLessThanOrEqualTo(pool.getMaximumPoolSize());
            return pagina;
        } finally { estatisticas.setStatisticsEnabled(habilitadas); }
    }

    private long fonte(String uri, String identificador) throws Exception {
        var result = enviar(uri, Map.of("identificador", identificador, "origem", "SIMULADA"), 201);
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult enviar(String uri, Object corpo, int esperado) throws Exception {
        var request = post(uri).session(sessao).header(csrf.headerName(), csrf.token());
        if (corpo != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo));
        return mvc.perform(request).andExpect(status().is(esperado)).andReturn();
    }

    private void agir(String uri, Object corpo) throws Exception {
        var request = patch(uri).session(sessao).header(csrf.headerName(), csrf.token());
        if (corpo != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo));
        mvc.perform(request).andExpect(status().isOk());
    }

    private void autenticar() throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", String.class);
        var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
        csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }

    private Topologia criarTopologia() {
        return new TransactionTemplate(manager).execute(status -> {
            var origem = new Subestacao(); origem.setNome("Origem E2E fila"); origem.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(origem);
            var destino = new Subestacao(); destino.setNome("Destino E2E fila"); destino.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(destino);
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador E2E fila"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(origem); em.persist(alimentador);
            var trafo = new Transformador(); trafo.setNome("Transformador E2E fila"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            trafo.setCapacidadeMva(BigDecimal.TEN); trafo.setAlimentador(alimentador); em.persist(trafo);
            var linha = new LinhaTransmissao(); linha.setNome("Linha E2E fila"); linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            linha.setTensaoOperacaoKv(new BigDecimal("100")); linha.setSubestacaoOrigem(origem); linha.setSubestacaoDestino(destino); em.persist(linha);
            em.flush();
            return new Topologia(origem.getId(), destino.getId(), alimentador.getId(), trafo.getId(), linha.getId());
        });
    }

    private void limpar(Topologia t, Long genericoId) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            if (genericoId != null) {
                jdbc.update("DELETE FROM historico_alerta WHERE alerta_id = ?", genericoId);
                jdbc.update("DELETE FROM alerta WHERE id = ?", genericoId);
            }
            jdbc.update("DELETE FROM alerta_comunicacao WHERE ativo_id IN (?, ?)", t.linhaId(), t.transformadorId());
            jdbc.update("DELETE FROM alerta_sobrecarga WHERE ativo_id = ?", t.transformadorId());
            jdbc.update("DELETE FROM alerta_tensao WHERE ativo_id = ?", t.linhaId());
            jdbc.update("DELETE FROM medicao_transformador WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", t.transformadorId());
            jdbc.update("DELETE FROM medicao_linha WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", t.linhaId());
            jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id IN (?, ?)", t.linhaId(), t.transformadorId());
            jdbc.update("DELETE FROM transformador WHERE id = ?", t.transformadorId());
            jdbc.update("DELETE FROM linha_transmissao WHERE id = ?", t.linhaId());
            jdbc.update("DELETE FROM alimentador WHERE id = ?", t.alimentadorId());
            jdbc.update("DELETE FROM subestacao WHERE id IN (?, ?)", t.origemId(), t.destinoId());
            for (Long id : List.of(t.transformadorId(), t.linhaId(), t.alimentadorId(), t.origemId(), t.destinoId()))
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
        });
    }

    private record Topologia(Long origemId, Long destinoId, Long alimentadorId, Long transformadorId, Long linhaId) { }
}
