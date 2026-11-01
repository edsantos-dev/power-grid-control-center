package br.com.powergrid.controlcenter.service;

import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static br.com.powergrid.controlcenter.model.SituacaoEventoOperacional.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventoOperacionalTransicaoConcorrenteTest {
    @Autowired private EventoOperacionalService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deveConfirmarSomenteUmaTransicaoQuandoDuasSolicitacoesEsperamAberto() throws Exception {
        var tx = new TransactionTemplate(manager);
        Long[] ids = tx.execute(status -> {
            var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
            return new Long[] {evento.getId(), evento.getAtivo().getId(), evento.getResponsavelUsuario().getId()};
        });
        String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, ids[2]);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            Callable<Integer> tarefa = () -> {
                inicio.await(); EventosTransicaoFixture.autenticar(email);
                try {
                    service.transicionar(ids[0], ABERTO, EM_ANDAMENTO, "Atendimento concorrente"); return 201;
                } catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var primeira = executor.submit(tarefa); var segunda = executor.submit(tarefa); inicio.countDown();
            assertThat(java.util.List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, ids[0])).isEqualTo("EM_ANDAMENTO");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional WHERE evento_id = ?", Long.class, ids[0])).isEqualTo(1);
            var historico = jdbc.queryForObject("SELECT alterado_em FROM historico_evento_operacional WHERE evento_id = ?", OffsetDateTime.class, ids[0]);
            assertThat(jdbc.queryForObject("SELECT inicio_efetivo FROM evento_operacional WHERE id = ?", OffsetDateTime.class, ids[0]).toInstant()).isEqualTo(historico.toInstant());
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
