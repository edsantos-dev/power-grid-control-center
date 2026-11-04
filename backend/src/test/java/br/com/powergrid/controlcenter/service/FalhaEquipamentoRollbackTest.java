package br.com.powergrid.controlcenter.service;

import java.time.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.*;
import br.com.powergrid.controlcenter.support.*;
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
class FalhaEquipamentoRollbackTest {
    @Autowired private FalhaEquipamentoService service;
    @Autowired private EventoOperacionalService eventos;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoSpyBean private FalhaEquipamentoRepository falhas;
    @MockitoSpyBean private HistoricoEventoOperacionalRepository historicos;
    @MockitoBean private Clock clock;

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"REGISTRO", "CONCLUSAO"})
    void deveReverterAtivoEventoComposicaoEAuditoriaSePersistenciaFalhar(String etapa) {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-04T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        var d = tx.execute(status -> FalhaEquipamentoFixture.criar(em));
        try {
            EventosTransicaoFixture.autenticar(d.email());
            if (etapa.equals("REGISTRO")) {
                doAnswer(invocation -> {
                    var falha = invocation.getArgument(0, FalhaEquipamento.class);
                    em.persist(falha); em.flush();
                    assertThat(estado(d.ativoId())).isEqualTo("FALHA");
                    jdbc.update("INSERT INTO falha_equipamento (evento_operacional_id, estado_anterior, registrado_em) VALUES (?, ?, ?)",
                            falha.getId(), falha.getEstadoAnterior().name(), falha.getRegistradoEm());
                    return falha;
                }).when(falhas).saveAndFlush(any(FalhaEquipamento.class));
                assertThrows(DataIntegrityViolationException.class, () -> service.registrar(d.ativoId(), d.usuarioId(), "Defeito", null));
                assertThat(estado(d.ativoId())).isEqualTo("OPERACIONAL");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_operacional WHERE ativo_id = ?", Long.class, d.ativoId())).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM falha_equipamento f JOIN evento_operacional e ON e.id=f.evento_operacional_id WHERE e.ativo_id = ?", Long.class, d.ativoId())).isZero();
            } else {
                Long eventoId = service.registrar(d.ativoId(), d.usuarioId(), "Defeito", null).getId();
                eventos.transicionar(eventoId, SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Atendimento");
                doAnswer(invocation -> {
                    var h = invocation.getArgument(0, HistoricoEventoOperacional.class);
                    em.persist(h); em.flush();
                    assertThat(estado(d.ativoId())).isEqualTo("OPERACIONAL");
                    assertThat(jdbc.queryForObject("SELECT estado_apos_reparo FROM falha_equipamento WHERE evento_operacional_id = ?", String.class, eventoId)).isEqualTo("OPERACIONAL");
                    jdbc.update("INSERT INTO historico_evento_operacional (id, evento_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            h.getId(), eventoId, d.usuarioId(), h.getSituacaoAnterior().name(), h.getSituacaoNova().name(), h.getAlteradoEm(), h.getComentario());
                    return h;
                }).when(historicos).saveAndFlush(any(HistoricoEventoOperacional.class));
                assertThrows(DataIntegrityViolationException.class, () -> eventos.transicionar(eventoId,
                        SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO, "Conserto", null, EstadoOperacional.OPERACIONAL));
                assertThat(estado(d.ativoId())).isEqualTo("FALHA");
                assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, eventoId)).isEqualTo("EM_ANDAMENTO");
                assertThat(jdbc.queryForObject("SELECT termino_efetivo FROM evento_operacional WHERE id = ?", OffsetDateTime.class, eventoId)).isNull();
                assertThat(jdbc.queryForObject("SELECT estado_apos_reparo FROM falha_equipamento WHERE evento_operacional_id = ?", String.class, eventoId)).isNull();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional WHERE evento_id = ?", Long.class, eventoId)).isEqualTo(1);
            }
        } finally { tx.executeWithoutResult(status -> FalhaEquipamentoFixture.limpar(jdbc, d)); }
    }

    private String estado(Long id) { return jdbc.queryForObject("SELECT estado_operacional FROM ativo_rede WHERE id = ?", String.class, id); }
}
