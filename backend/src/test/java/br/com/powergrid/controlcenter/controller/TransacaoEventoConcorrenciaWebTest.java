package br.com.powergrid.controlcenter.controller;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TransacaoEventoConcorrenciaWebTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duasRequisicoesComMesmaSituacaoEsperadaDevemRetornar201E409() throws Exception {
        var tx = new TransactionTemplate(manager);
        Long[] ids = tx.execute(status -> {
            var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
            return new Long[] {evento.getId(), evento.getAtivo().getId(), evento.getResponsavelUsuario().getId()};
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, ids[2]);
            mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
            var csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            var corpo = objectMapper.writeValueAsString(Map.of("situacaoEsperada", "ABERTO", "situacaoDestino", "EM_ANDAMENTO", "comentario", "Atendimento concorrente"));
            var inicio = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> tarefa = () -> {
                inicio.await();
                return mockMvc.perform(post("/api/eventos-operacionais/{id}/transicoes", ids[0]).session(sessao)
                        .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                        .andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(tarefa); var segunda = executor.submit(tarefa); inicio.countDown();
            assertThat(java.util.List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, ids[0])).isEqualTo("EM_ANDAMENTO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional WHERE evento_id = ?", Long.class, ids[0])).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT usuario_id FROM historico_evento_operacional WHERE evento_id = ?", Long.class, ids[0])).isEqualTo(ids[2]);
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id = ?", ids[0]);
                jdbc.update("DELETE FROM evento_operacional WHERE id = ?", ids[0]);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[1]); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
            });
        }
    }
}
