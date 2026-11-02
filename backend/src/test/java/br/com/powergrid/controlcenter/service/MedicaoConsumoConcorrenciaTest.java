package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.*;
import br.com.powergrid.controlcenter.dto.MedicaoConsumoRequestDTO;
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture;
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

@SpringBootTest
class MedicaoConsumoConcorrenciaTest {
    @Autowired private MedicaoConsumoService service;
    @Autowired private AlertaComunicacaoService comunicacao;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void fontesConcorrentesDoMesmoMedidorDevemConfirmarSomenteUmaLeituraSobreposta() throws Exception {
        var tx = new TransactionTemplate(manager); var dados = tx.execute(status -> ConsumoMedidorFixture.criar(em, false));
        var executor = Executors.newFixedThreadPool(2); var inicio = new CountDownLatch(1);
        try {
            java.util.function.Function<Long, Callable<Integer>> tarefa = fonteId -> () -> {
                inicio.await(); ConsumoMedidorFixture.autenticar();
                try { service.ingerir(dados.medidorId(), request(fonteId)); return 201; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a = executor.submit(tarefa.apply(dados.fonteId())); var b = executor.submit(tarefa.apply(dados.segundaFonteId())); inicio.countDown();
            int statusA = a.get(30, TimeUnit.SECONDS), statusB = b.get(30, TimeUnit.SECONDS);
            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medicao_consumo_medidor WHERE fonte_id IN (?, ?)", Long.class,
                    dados.fonteId(), dados.segundaFonteId())).isEqualTo(1);
            Long vencedor = statusA == 201 ? dados.fonteId() : dados.segundaFonteId();
            Long rejeitado = statusA == 201 ? dados.segundaFonteId() : dados.fonteId();
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, vencedor)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, rejeitado)).isNull();
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> ConsumoMedidorFixture.limpar(jdbc, dados));
        }
    }

    @Test
    void jobEIngestaoConcorrentesDevemPreservarRestabelecimentoSemDuplicarEpisodio() throws Exception {
        var tx = new TransactionTemplate(manager); var dados = tx.execute(status -> ConsumoMedidorFixture.criar(em, true));
        var executor = Executors.newFixedThreadPool(2); var inicio = new CountDownLatch(1);
        try {
            var job = executor.submit(() -> { inicio.await(); return comunicacao.avaliar(); });
            var ingestao = executor.submit(() -> {
                inicio.await(); ConsumoMedidorFixture.autenticar();
                try { return service.ingerir(dados.medidorId(), request(dados.fonteId())); }
                finally { SecurityContextHolder.clearContext(); }
            });
            inicio.countDown(); job.get(30, TimeUnit.SECONDS); var medicao = ingestao.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta_comunicacao WHERE fonte_id = ?", Long.class, dados.fonteId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE id = ?", String.class, dados.alertaId())).isEqualTo("RESTABELECIDO");
            assertThat(jdbc.queryForObject("SELECT restabelecido_em FROM alerta_comunicacao WHERE id = ?", OffsetDateTime.class, dados.alertaId()))
                    .isEqualTo(medicao.recebidoEm());
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> ConsumoMedidorFixture.limpar(jdbc, dados));
        }
    }

    private MedicaoConsumoRequestDTO request(Long fonteId) {
        return new MedicaoConsumoRequestDTO(fonteId, OffsetDateTime.parse("2026-01-02T10:00:00Z"),
                OffsetDateTime.parse("2026-01-02T11:00:00Z"), BigDecimal.TEN);
    }
}
