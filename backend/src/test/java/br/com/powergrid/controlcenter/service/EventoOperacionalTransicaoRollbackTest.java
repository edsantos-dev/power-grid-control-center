package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static br.com.powergrid.controlcenter.model.SituacaoEventoOperacional.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest
class EventoOperacionalTransicaoRollbackTest {
    @Autowired private EventoOperacionalService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private HistoricoEventoOperacionalRepository historicos;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00.123456Z");

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void limparAutenticacao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveDesfazerAtualizacaoDeEstadoEDatasQuandoHistoricoFalhar(boolean concluir) {
        var tx = new TransactionTemplate(manager);
        Long[] ids = tx.execute(status -> {
            var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
            if (concluir) {
                evento.setSituacao("EM_ANDAMENTO"); evento.setInicioEfetivo(AGORA.minusSeconds(60).atOffset(ZoneOffset.UTC)); em.flush();
            }
            EventosTransicaoFixture.autenticar(evento.getResponsavelUsuario().getEmail());
            return new Long[] {evento.getId(), evento.getAtivo().getId()};
        });
        try {
            doAnswer(invocation -> {
                assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, ids[0]))
                        .isEqualTo(concluir ? "CONCLUIDO" : "EM_ANDAMENTO");
                assertThat(jdbc.queryForObject("SELECT " + (concluir ? "termino_efetivo" : "inicio_efetivo") + " FROM evento_operacional WHERE id = ?", OffsetDateTime.class, ids[0]).toInstant())
                        .isEqualTo(AGORA);
                throw new IllegalStateException("Falha simulada no histórico");
            }).when(historicos).saveAndFlush(any(HistoricoEventoOperacional.class));
            assertThrows(IllegalStateException.class, () -> service.transicionar(ids[0], concluir ? EM_ANDAMENTO : ABERTO,
                    concluir ? CONCLUIDO : EM_ANDAMENTO, "Atendimento"));
            verify(historicos).saveAndFlush(any(HistoricoEventoOperacional.class));
            assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, ids[0]))
                    .isEqualTo(concluir ? "EM_ANDAMENTO" : "ABERTO");
            assertThat(jdbc.queryForObject("SELECT termino_efetivo FROM evento_operacional WHERE id = ?", OffsetDateTime.class, ids[0])).isNull();
            var inicio = jdbc.queryForObject("SELECT inicio_efetivo FROM evento_operacional WHERE id = ?", OffsetDateTime.class, ids[0]);
            if (concluir) assertThat(inicio.toInstant()).isEqualTo(AGORA.minusSeconds(60));
            else assertThat(inicio).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional WHERE evento_id = ?", Long.class, ids[0])).isZero();
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id = ?", ids[0]);
                jdbc.update("DELETE FROM evento_operacional WHERE id = ?", ids[0]);
                jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[1]); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[1]);
            });
        }
    }
}
