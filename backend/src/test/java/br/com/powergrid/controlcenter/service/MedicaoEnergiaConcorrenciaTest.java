package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.*;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.support.EnergiaAlimentadorFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MedicaoEnergiaConcorrenciaTest {
    @Autowired private MedicaoEnergiaService service;
    @Autowired private AlimentadorRepository alimentadores;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duasFontesConcorrentesNaoDevemGravarPeriodosSobrepostosNoMesmoAlimentador() throws Exception {
        var tx = new TransactionTemplate(manager); Long[] ids = tx.execute(status -> EnergiaAlimentadorFixture.criar(em));
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        var request = new MedicaoEnergiaRequestDTO(agora.minusHours(2), agora.minusHours(1), BigDecimal.TEN);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            java.util.function.Function<Long, Callable<Integer>> tarefa = fonteId -> () -> {
                inicio.await(); EnergiaAlimentadorFixture.autenticar();
                try { service.ingerir(ids[0], fonteId, request); return 201; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a = executor.submit(tarefa.apply(ids[2])); var b = executor.submit(tarefa.apply(ids[3])); inicio.countDown();
            int statusA = a.get(30, TimeUnit.SECONDS), statusB = b.get(30, TimeUnit.SECONDS);
            assertThat(java.util.List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("select count(*) from medicao_energia_alimentador where fonte_id in (?, ?)", Long.class, ids[2], ids[3])).isEqualTo(1);
            Long vencedor = statusA == 201 ? ids[2] : ids[3]; Long rejeitado = statusA == 201 ? ids[3] : ids[2];
            assertThat(jdbc.queryForObject("select fonte_id from medicao_energia_alimentador where fonte_id in (?, ?)", Long.class, ids[2], ids[3])).isEqualTo(vencedor);
            assertThat(jdbc.queryForObject("select ultima_comunicacao_em from fonte_medicao where id = ?", OffsetDateTime.class, vencedor)).isNotNull();
            assertThat(jdbc.queryForObject("select ultima_comunicacao_em from fonte_medicao where id = ?", OffsetDateTime.class, rejeitado)).isNull();
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> EnergiaAlimentadorFixture.limpar(jdbc, ids));
        }
    }

    @Test
    void ingestaoDeveAguardarConfiguracaoEUsarParametroConfirmado() throws Exception {
        var tx = new TransactionTemplate(manager); Long[] ids = tx.execute(status -> EnergiaAlimentadorFixture.criar(em));
        var bloqueado = new CountDownLatch(1); var liberar = new CountDownLatch(1); var iniciou = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var configuracao = executor.submit(() -> {
                EnergiaAlimentadorFixture.autenticar();
                try { tx.executeWithoutResult(status -> {
                    alimentadores.buscarPorIdComBloqueio(ids[0]).orElseThrow();
                    service.configurar(ids[0], new AlimentadorParametroRequestDTO(new BigDecimal("9.654321"))); em.flush();
                    bloqueado.countDown(); aguardar(liberar);
                }); } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var agora = OffsetDateTime.now(ZoneOffset.UTC);
            var ingestao = executor.submit(() -> {
                EnergiaAlimentadorFixture.autenticar(); iniciou.countDown();
                try { return service.ingerir(ids[0], ids[2], new MedicaoEnergiaRequestDTO(agora.minusHours(2), agora.minusHours(1), BigDecimal.TEN)); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(iniciou.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> ingestao.get(150, TimeUnit.MILLISECONDS)); liberar.countDown();
            configuracao.get(30, TimeUnit.SECONDS);
            assertThat(ingestao.get(30, TimeUnit.SECONDS).percentualPerdaReferencia()).isEqualByComparingTo("9.654321");
        } finally {
            liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> EnergiaAlimentadorFixture.limpar(jdbc, ids));
        }
    }

    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo limite de sincronização."); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
