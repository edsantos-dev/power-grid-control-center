package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaRequestDTO;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorRequestDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ComunicacaoIngestaoConcorrenteTest {
    @Autowired private AlertaComunicacaoService avaliacao;
    @Autowired private MedicaoLinhaService linhas;
    @Autowired private MedicaoTransformadorService transformadores;
    @Autowired private FonteMedicaoRepository fontes;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"LINHA,true", "LINHA,false", "TRANSFORMADOR,true", "TRANSFORMADOR,false"})
    void deveSerializarJobEIngestaoSemDeadlockOuDuplicatas(String tipo, boolean ingestaoPrimeiro) throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> preparar(tipo));
        var executor = Executors.newFixedThreadPool(2);
        var bloqueado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try {
            var primeiro = executor.submit(() -> tx.execute(status -> {
                fontes.buscarPorIdComBloqueio(ids[0]).orElseThrow();
                if (ingestaoPrimeiro) ingerir(tipo, ids); else avaliacao.avaliar();
                bloqueado.countDown();
                aguardar(liberar);
                return null;
            }));
            assertThat(bloqueado.await(30, TimeUnit.SECONDS)).isTrue();
            var iniciou = new CountDownLatch(1);
            var segundo = executor.submit(() -> {
                iniciou.countDown();
                if (ingestaoPrimeiro) avaliacao.avaliar(); else ingerir(tipo, ids);
                return null;
            });
            assertThat(iniciou.await(30, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> segundo.get(200, TimeUnit.MILLISECONDS));
            liberar.countDown();
            primeiro.get(30, TimeUnit.SECONDS); segundo.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta_comunicacao WHERE fonte_id = ? AND status = 'ATIVO'", Long.class, ids[0])).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta_comunicacao WHERE fonte_id = ?", Long.class, ids[0]))
                    .isEqualTo(ingestaoPrimeiro ? 0 : 1);
            String tabela = tipo.equals("LINHA") ? "medicao_linha" : "medicao_transformador";
            var recebido = jdbc.queryForObject("SELECT recebido_em FROM " + tabela + " WHERE fonte_id = ?", OffsetDateTime.class, ids[0]);
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, ids[0]).toInstant())
                    .isEqualTo(recebido.toInstant());
            if (!ingestaoPrimeiro) {
                assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, ids[0])).isEqualTo("RESTABELECIDO");
                assertThat(jdbc.queryForObject("SELECT restabelecido_em FROM alerta_comunicacao WHERE fonte_id = ?", OffsetDateTime.class, ids[0]).toInstant())
                        .isEqualTo(recebido.toInstant());
            }
        } finally {
            liberar.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> limpar(tipo, ids));
        }
    }

    private void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo esgotado aguardando liberação do teste.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(ex);
        }
    }

    private void ingerir(String tipo, Long[] ids) {
        var medido = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        if (tipo.equals("LINHA")) linhas.ingerir(ids[1], new MedicaoLinhaRequestDTO(ids[0], medido, new BigDecimal("100"), null, null, null, null));
        else transformadores.ingerir(ids[1], new MedicaoTransformadorRequestDTO(ids[0], medido, new BigDecimal("8")));
    }

    private Long[] preparar(String tipo) {
        var fonte = new FonteMedicao(); fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setIdentificador("FONTE-CONCORRENTE");
        fonte.setMonitoradaDesde(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        Long[] ids;
        if (tipo.equals("LINHA")) {
            var linha = linha(); fonte.setAtivo(linha);
            ids = new Long[] {null, linha.getId(), linha.getSubestacaoOrigem().getId(), linha.getSubestacaoDestino().getId()};
        } else {
            var sub = new Subestacao(); sub.setNome("Subestação"); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); entityManager.persist(sub);
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(sub); entityManager.persist(alimentador);
            var trafo = new Transformador(); trafo.setNome("Transformador"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            trafo.setCapacidadeMva(BigDecimal.TEN); trafo.setAlimentador(alimentador); entityManager.persist(trafo); fonte.setAtivo(trafo);
            ids = new Long[] {null, trafo.getId(), alimentador.getId(), sub.getId()};
        }
        entityManager.persist(fonte); entityManager.flush(); ids[0] = fonte.getId(); return ids;
    }

    private void limpar(String tipo, Long[] ids) {
        jdbc.update("DELETE FROM alerta_comunicacao WHERE fonte_id = ?", ids[0]);
        jdbc.update("DELETE FROM alerta_tensao WHERE ativo_id = ?", ids[1]);
        jdbc.update("DELETE FROM alerta_sobrecarga WHERE ativo_id = ?", ids[1]);
        jdbc.update("DELETE FROM medicao_linha WHERE fonte_id = ?", ids[0]);
        jdbc.update("DELETE FROM medicao_transformador WHERE fonte_id = ?", ids[0]);
        jdbc.update("DELETE FROM fonte_medicao WHERE id = ?", ids[0]);
        if (tipo.equals("LINHA")) {
            jdbc.update("DELETE FROM linha_transmissao WHERE id = ?", ids[1]);
            jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
            jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[2]);
        } else {
            jdbc.update("DELETE FROM transformador WHERE id = ?", ids[1]);
            jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
            jdbc.update("DELETE FROM alimentador WHERE id = ?", ids[2]);
        }
        jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[2]);
        jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[3]);
        jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[3]);
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

}
