package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorRequestDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.AlertaSobrecargaRepository;
import br.com.powergrid.controlcenter.repository.MedicaoTransformadorRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest
class AlertaSobrecargaTransacaoTest {
    @Autowired private MedicaoTransformadorService service;
    @Autowired private MedicaoTransformadorRepository medicoes;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AlertaSobrecargaRepository alertas;

    @Test
    void deveDesfazerMedicaoQuandoGravacaoDoAlertaFalhar() {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            FonteMedicao f = fonte(); Transformador t = (Transformador) f.getAtivo();
            prepararComunicacao(f);
            return new Long[] {f.getId(), t.getId(), t.getAlimentador().getId(), t.getAlimentador().getSubestacao().getId()};
        });
        try {
            long medicoesAntes = medicoes.count(); long alertasAntes = alertas.count();
            doAnswer(invocation -> {
                assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, ids[0]))
                        .isEqualTo("RESTABELECIDO");
                throw new IllegalStateException("Falha simulada na persistência do alerta");
            }).when(alertas).saveAndFlush(any(AlertaSobrecarga.class));
            var request = new MedicaoTransformadorRequestDTO(ids[0], OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1), new BigDecimal("10.8"));
            assertThrows(IllegalStateException.class, () -> service.ingerir(ids[1], request));
            verify(alertas).saveAndFlush(any(AlertaSobrecarga.class));
            assertThat(medicoes.count()).isEqualTo(medicoesAntes);
            assertThat(alertas.count()).isEqualTo(alertasAntes);
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, ids[0]).toInstant())
                    .isEqualTo(OffsetDateTime.parse("2026-01-01T10:00:00Z").toInstant());
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, ids[0]))
                    .isEqualTo("ATIVO");
            assertThat(jdbc.queryForObject("SELECT restabelecido_em FROM alerta_comunicacao WHERE fonte_id = ?", OffsetDateTime.class, ids[0]))
                    .isNull();
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM alerta_sobrecarga WHERE ativo_id = ?", ids[1]);
                jdbc.update("DELETE FROM medicao_transformador WHERE fonte_id = ?", ids[0]);
                jdbc.update("DELETE FROM alerta_comunicacao WHERE fonte_id = ?", ids[0]);
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
    private void prepararComunicacao(FonteMedicao fonte) {
        var anterior = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        fonte.setMonitoradaDesde(anterior.minusHours(1)); fonte.setUltimaComunicacaoEm(anterior);
        entityManager.flush();
        jdbc.update("INSERT INTO alerta_comunicacao (fonte_id, ativo_id, monitorada_desde, ultima_comunicacao_em, tolerancia_segundos, detectado_em, status) VALUES (?, ?, ?, ?, 300, ?, 'ATIVO')",
                fonte.getId(), fonte.getAtivo().getId(), fonte.getMonitoradaDesde(), anterior, anterior.plusMinutes(6));
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
