package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.model.HistoricoAlerta;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest
class TratativaAlertaRollbackTest {
    @Autowired private TratativaAlertaService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoSpyBean private HistoricoAlertaRepository historicos;
    @MockitoBean private Clock clock;

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveReverterSituacaoEHistoricoQuandoGravacaoDaAuditoriaFalhar(boolean resolver) {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-03T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        var origem = resolver ? SituacaoAlerta.RECONHECIDO : SituacaoAlerta.ABERTO;
        var destino = resolver ? SituacaoAlerta.RESOLVIDO : SituacaoAlerta.RECONHECIDO;
        var d = tx.execute(status -> {
            var dados = TratativaAlertaFixture.criar(em, origem, true);
            if (resolver) TratativaAlertaFixture.registrarReconhecimento(em, dados);
            return dados;
        });
        long anteriores = resolver ? 1 : 0;
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, d.operadorId());
            EventosTransicaoFixture.autenticar(email);
            doAnswer(invocation -> {
                var h = invocation.getArgument(0, HistoricoAlerta.class);
                assertThat(jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, d.alertaId())).isEqualTo(destino.name());
                String sql = "INSERT INTO historico_alerta (alerta_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario)"
                        + " VALUES (?, ?, ?, ?, ?, ?)";
                Object[] parametros = {d.alertaId(), h.getUsuario().getId(), h.getSituacaoAnterior().name(),
                        h.getSituacaoNova().name(), h.getAlteradoEm(), h.getComentario()};
                jdbc.update(sql, parametros);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, d.alertaId()))
                        .isEqualTo(anteriores + 1);
                jdbc.update(sql, parametros);
                return h;
            }).when(historicos).saveAndFlush(any(HistoricoAlerta.class));

            assertThrows(DataIntegrityViolationException.class, () -> {
                if (resolver) service.resolver(d.alertaId(), d.operadorId(), "Resolução revertida");
                else service.reconhecer(d.alertaId(), d.operadorId(), "Reconhecimento revertido");
            });
            verify(historicos).saveAndFlush(any(HistoricoAlerta.class));
            assertThat(jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, d.alertaId())).isEqualTo(origem.name());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, d.alertaId())).isEqualTo(anteriores);
            assertThat(jdbc.queryForObject("SELECT estado_operacional FROM ativo_rede WHERE id = ?", String.class, d.ativoId())).isEqualTo("OPERACIONAL");
            if (resolver) {
                assertThat(jdbc.queryForObject("SELECT comentario FROM historico_alerta WHERE alerta_id = ?", String.class, d.alertaId()))
                        .isEqualTo("Reconhecimento anterior");
                assertThat(jdbc.queryForObject("SELECT usuario_id FROM historico_alerta WHERE alerta_id = ?", Long.class, d.alertaId()))
                        .isEqualTo(d.operadorId());
            }
        } finally {
            tx.executeWithoutResult(status -> TratativaAlertaFixture.limpar(jdbc, d));
        }
    }
}
