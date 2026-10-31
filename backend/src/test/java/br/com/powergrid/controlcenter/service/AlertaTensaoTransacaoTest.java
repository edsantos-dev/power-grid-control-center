package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaRequestDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.AlertaTensaoRepository;
import br.com.powergrid.controlcenter.repository.MedicaoLinhaRepository;
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
class AlertaTensaoTransacaoTest {
    @Autowired private MedicaoLinhaService service;
    @Autowired private MedicaoLinhaRepository medicoes;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AlertaTensaoRepository alertas;

    @Test
    void deveDesfazerMedicaoQuandoGravacaoDoAlertaFalhar() {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            FonteMedicao f = fonte(); entityManager.persist(f); entityManager.flush(); LinhaTransmissao t = (LinhaTransmissao) f.getAtivo();
            prepararComunicacao(f);
            return new Long[] {f.getId(), t.getId(), t.getSubestacaoOrigem().getId(), t.getSubestacaoDestino().getId()};
        });
        try {
            long medicoesAntes = medicoes.count(); long alertasAntes = alertas.count();
            doAnswer(invocation -> {
                assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE fonte_id = ?", String.class, ids[0]))
                        .isEqualTo("RESTABELECIDO");
                throw new IllegalStateException("Falha simulada na persistência do alerta");
            }).when(alertas).saveAndFlush(any(AlertaTensao.class));
            var request = new MedicaoLinhaRequestDTO(ids[0], OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1), new BigDecimal("90"), null, null, null, null);
            assertThrows(IllegalStateException.class, () -> service.ingerir(ids[1], request));
            verify(alertas).saveAndFlush(any(AlertaTensao.class));
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
                jdbc.update("DELETE FROM alerta_tensao WHERE ativo_id = ?", ids[1]);
                jdbc.update("DELETE FROM medicao_linha WHERE fonte_id = ?", ids[0]);
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
    private void prepararComunicacao(FonteMedicao fonte) {
        var anterior = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        fonte.setMonitoradaDesde(anterior.minusHours(1)); fonte.setUltimaComunicacaoEm(anterior);
        entityManager.flush();
        jdbc.update("INSERT INTO alerta_comunicacao (fonte_id, ativo_id, monitorada_desde, ultima_comunicacao_em, tolerancia_segundos, detectado_em, status) VALUES (?, ?, ?, ?, 300, ?, 'ATIVO')",
                fonte.getId(), fonte.getAtivo().getId(), fonte.getMonitoradaDesde(), anterior, anterior.plusMinutes(6));
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
