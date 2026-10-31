package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AvaliacaoComunicacaoConcorrenteTest {
    @Autowired private AlertaComunicacaoService service;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deveCriarSomenteUmEpisodioAtivoEmDuasAvaliacoesConcorrentes() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            var fonte = fonte();
            fonte.setMonitoradaDesde(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
            entityManager.persist(fonte); entityManager.flush();
            var linha = (LinhaTransmissao) fonte.getAtivo();
            return new Long[] {fonte.getId(), linha.getId(), linha.getSubestacaoOrigem().getId(), linha.getSubestacaoDestino().getId()};
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            var primeira = executor.submit(() -> { inicio.await(); return service.avaliar(); });
            var segunda = executor.submit(() -> { inicio.await(); return service.avaliar(); });
            inicio.countDown();
            var a = primeira.get(30, TimeUnit.SECONDS);
            var b = segunda.get(30, TimeUnit.SECONDS);
            assertThat(a.alertasCriados() + b.alertasCriados()).isEqualTo(1);
            assertThat(a.alertasAtivosExistentes() + b.alertasAtivosExistentes()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta_comunicacao WHERE fonte_id = ? AND status = 'ATIVO'", Long.class, ids[0])).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM alerta_comunicacao WHERE fonte_id = ?", ids[0]);
                jdbc.update("DELETE FROM fonte_medicao WHERE id = ?", ids[0]);
                jdbc.update("DELETE FROM linha_transmissao WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[2]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[2]);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[3]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[3]);
            });
        }
    }

    private LinhaTransmissao linha() {
        Subestacao a = new Subestacao();
        a.setNome("Origem");
        a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(a);
        Subestacao b = new Subestacao();
        b.setNome("Destino");
        b.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(b);
        LinhaTransmissao linha = new LinhaTransmissao();
        linha.setNome("Linha");
        linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setSubestacaoOrigem(a);
        linha.setSubestacaoDestino(b);
        linha.setExtensaoKm(new BigDecimal("100"));
        linha.setTensaoOperacaoKv(new BigDecimal("100"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

    private FonteMedicao fonte() {
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador("SENSOR-01");
        fonte.setAtivo(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }

}
