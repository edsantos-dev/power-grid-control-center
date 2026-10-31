package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorRequestDTO;
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
class AlertaSobrecargaReconhecimentoConcorrenteTest {
    @Autowired private AlertaSobrecargaService service;
    @Autowired private MedicaoTransformadorService medicoes;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void devePreservarPrimeiroReconhecimentoEmDuasTransacoesConcorrentes() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            FonteMedicao f = fonte(); Transformador t = (Transformador) f.getAtivo();
            var m = medicoes.ingerir(t.getId(), new MedicaoTransformadorRequestDTO(f.getId(),
                    OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1), new BigDecimal("10.8")));
            Long alertaId = entityManager.createQuery("select a.id from AlertaSobrecarga a where a.medicao.id = :id", Long.class)
                    .setParameter("id", m.id()).getSingleResult();
            var usuarios = entityManager.createQuery("select u.id from Usuario u order by u.id", Long.class).setMaxResults(2).getResultList();
            return new Long[] {f.getId(), t.getId(), t.getAlimentador().getId(), t.getAlimentador().getSubestacao().getId(),
                    alertaId, usuarios.get(0), usuarios.get(1)};
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            var primeira = executor.submit(() -> { inicio.await(); return service.reconhecer(ids[4], ids[5]); });
            var segunda = executor.submit(() -> { inicio.await(); return service.reconhecer(ids[4], ids[6]); });
            inicio.countDown();
            var a = primeira.get(30, TimeUnit.SECONDS); var b = segunda.get(30, TimeUnit.SECONDS);
            assertThat(a.status()).isEqualTo(StatusAlerta.RECONHECIDO);
            assertThat(a.reconhecidoPorUsuarioId()).isIn(ids[5], ids[6]);
            assertThat(b.reconhecidoPorUsuarioId()).isEqualTo(a.reconhecidoPorUsuarioId());
            assertThat(b.reconhecidoEm()).isEqualTo(a.reconhecidoEm());
            var salvo = service.buscarPorId(ids[4]);
            assertThat(salvo.reconhecidoPorUsuarioId()).isEqualTo(a.reconhecidoPorUsuarioId());
            assertThat(salvo.reconhecidoEm()).isEqualTo(a.reconhecidoEm());
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM alerta_sobrecarga WHERE ativo_id = ?", ids[1]);
                jdbc.update("DELETE FROM medicao_transformador WHERE fonte_id = ?", ids[0]);
                jdbc.update("DELETE FROM fonte_medicao WHERE id = ?", ids[0]);
                jdbc.update("DELETE FROM transformador WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM alimentador WHERE id = ?", ids[2]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[2]);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[3]);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[3]);
            });
        }
    }
    private FonteMedicao fonte() {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação"); subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(subestacao); entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador"); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(transformador);
        FonteMedicao fonte = new FonteMedicao(); fonte.setIdentificador("SIMULADOR-TRAFO");
        fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setAtivo(transformador);
        entityManager.persist(fonte); entityManager.flush(); return fonte;
    }

}
