package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.*;
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
class FalhaEquipamentoFluxoE2ETest {
    private static final Instant AGORA = Instant.parse("2026-11-04T12:00:00.123456Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void deveIniciarManobraRegistrarFalhaBloquearInstalacaoEConcluirEmManutencao() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        var d = tx.execute(status -> {
            var sub = new Subestacao(); base(sub); em.persist(sub);
            var al = new Alimentador(); base(al); al.setSubestacao(sub); em.persist(al);
            var trafo = new Transformador(); base(trafo); trafo.setAlimentador(al); trafo.setCapacidadeMva(BigDecimal.TEN); em.persist(trafo);
            var consumidor = new Consumidor(); consumidor.setNomeRazaoSocial("Titular do E2E de falha");
            consumidor.setTipoPessoa(Consumidor.TipoPessoa.PF); consumidor.setDocumento("11144477735"); em.persist(consumidor); em.flush();
            Long operador = em.createQuery("select u.id from Usuario u where u.perfil=:perfil", Long.class).setParameter("perfil", PerfilAcesso.OPERADOR).getSingleResult();
            return new Dados(sub.getId(), al.getId(), trafo.getId(), consumidor.getId(), operador);
        });
        Long medidorId = null;
        try {
            autenticar();
            var manobra = postJson("/api/eventos-operacionais", Map.of("ativoId", d.trafo(), "categoria", "MANOBRA", "descricao", "Manobra planejada",
                    "responsavelUsuarioId", d.operador(), "inicioPrevisto", AGORA.plusSeconds(3600).toString()), 201);
            Long manobraId = mapper.readValue(manobra.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class).id();
            transicao(manobraId, Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Manobra iniciada"), 201);
            var falhaCriada = postJson("/api/falhas-equipamento", Map.of("ativoId", d.trafo(), "responsavelUsuarioId", d.operador(),
                    "descricao", "Defeito observado durante operação", "criticidade", "ALTA"), 201);
            var falha = mapper.readValue(falhaCriada.getResponse().getContentAsString(), FalhaEquipamentoResponseDTO.class);
            assertThat(falhaCriada.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/falhas-equipamento/" + falha.id());
            assertThat(falha.id()).isEqualTo(falha.evento().id());
            assertThat(falha.estadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
            assertThat(falha.estadoOperacionalAtual()).isEqualTo(EstadoOperacional.FALHA);
            assertThat(falha.registradoEm().toInstant()).isEqualTo(AGORA);
            mvc.perform(get("/api/transformadores/{id}", d.trafo()).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estadoOperacional").value("FALHA"));
            postJson("/api/eventos-operacionais", Map.of("ativoId", d.trafo(), "categoria", "MANOBRA", "descricao", "Nova tentativa",
                    "responsavelUsuarioId", d.operador(), "inicioPrevisto", AGORA.plusSeconds(3600).toString()), 409);

            var medidor = postJson("/api/medidores", Map.of("nome", "Medidor para instalação", "numeroSerie", "FALHA-E2E-" + d.trafo()), 201);
            medidorId = mapper.readValue(medidor.getResponse().getContentAsString(), MedidorResponseDTO.class).id();
            var instalacao = Map.of("consumidorId", d.consumidor(), "transformadorId", d.trafo(), "latitude", 0, "longitude", 0, "dataInstalacao", "2026-01-01");
            instalar(medidorId, instalacao, 409);
            assertThat(jdbc.queryForObject("SELECT transformador_id FROM medidor_inteligente WHERE id = ?", Long.class, medidorId)).isNull();

            when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
            transicao(falha.id(), Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Reparo iniciado"), 201);
            when(clock.instant()).thenReturn(AGORA.plusSeconds(180));
            transicao(falha.id(), Map.of("situacaoEsperada", "EM_ANDAMENTO", "situacaoDestino", "CONCLUIDO", "comentario", "Reparo confirmado; permanece em manutenção",
                    "estadoAposReparo", "MANUTENCAO"), 201);
            var result = mvc.perform(get("/api/falhas-equipamento/{id}", falha.id()).session(sessao)).andExpect(status().isOk()).andReturn();
            var concluida = mapper.readValue(result.getResponse().getContentAsString(), FalhaEquipamentoResponseDTO.class);
            assertThat(concluida.estadoAposReparo()).isEqualTo(EstadoOperacional.MANUTENCAO);
            assertThat(concluida.estadoOperacionalAtual()).isEqualTo(EstadoOperacional.MANUTENCAO);
            assertThat(concluida.estadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
            assertThat(concluida.registradoEm()).isEqualTo(falha.registradoEm());
            assertThat(concluida.evento().situacao()).isEqualTo("CONCLUIDO");
            assertThat(concluida.evento().estadoAposReparo()).isEqualTo(EstadoOperacional.MANUTENCAO);
            assertThat(concluida.evento().terminoEfetivo().toInstant()).isEqualTo(AGORA.plusSeconds(180));
            mvc.perform(get("/api/transformadores/{id}", d.trafo()).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estadoOperacional").value("MANUTENCAO"));
            mvc.perform(get("/api/eventos-operacionais/{id}", falha.id()).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estadoAposReparo").value("MANUTENCAO"));
            mvc.perform(get("/api/falhas-equipamento").session(sessao).param("ativoId", d.trafo().toString()).param("situacao", "CONCLUIDO"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(falha.id()));
            mvc.perform(get("/api/eventos-operacionais/{id}/transicoes", falha.id()).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].situacaoNova").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.content[0].usuarioId").value(d.operador()));
            instalar(medidorId, instalacao, 200);
            assertThat(jdbc.queryForObject("SELECT estado_operacional FROM ativo_rede WHERE id = ?", String.class, d.trafo())).isEqualTo("MANUTENCAO");
        } finally {
            Long medidor = medidorId;
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", d.trafo());
                jdbc.update("DELETE FROM falha_equipamento WHERE evento_operacional_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", d.trafo());
                jdbc.update("DELETE FROM evento_operacional WHERE ativo_id = ?", d.trafo());
                if (medidor != null) { jdbc.update("DELETE FROM medidor_inteligente WHERE id = ?", medidor); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", medidor); }
                jdbc.update("DELETE FROM consumidor WHERE id = ?", d.consumidor());
                jdbc.update("DELETE FROM transformador WHERE id = ?", d.trafo());
                jdbc.update("DELETE FROM alimentador WHERE id = ?", d.al()); jdbc.update("DELETE FROM subestacao WHERE id = ?", d.sub());
                for (Long id : java.util.List.of(d.trafo(), d.al(), d.sub())) jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
            });
        }
    }

    private void transicao(Long id, Map<String, ?> corpo, int esperado) throws Exception { postJson("/api/eventos-operacionais/" + id + "/transicoes", corpo, esperado); }
    private MvcResult postJson(String uri, Object corpo, int esperado) throws Exception {
        return mvc.perform(post(uri).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().is(esperado)).andReturn();
    }
    private void instalar(Long id, Object corpo, int esperado) throws Exception {
        mvc.perform(patch("/api/medidores/{id}/instalacao", id).session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().is(esperado));
    }
    private void autenticar() throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "operador@powergrid.local").param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn(); csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
    private void base(AtivoRede ativo) { ativo.setNome("Ativo do E2E de falha"); ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); }
    private record Dados(Long sub, Long al, Long trafo, Long consumidor, Long operador) { }
}
