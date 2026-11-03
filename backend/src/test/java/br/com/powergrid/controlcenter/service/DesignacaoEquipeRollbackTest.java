package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import br.com.powergrid.controlcenter.model.HistoricoDesignacaoEquipe;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.HistoricoDesignacaoEquipeRepository;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
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
class DesignacaoEquipeRollbackTest {
    @Autowired private DesignacaoEquipeService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoSpyBean private HistoricoDesignacaoEquipeRepository historicos;
    @MockitoBean private Clock clock;

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"INCLUSAO", "SUBSTITUICAO", "RETIRADA"})
    void deveReverterEquipeVersaoEHistoricoSePersistenciaDoHistoricoFalhar(String operacao) {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-03T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(manager);
        var d = tx.execute(status -> {
            var dados = DesignacaoEquipeFixture.criar(em);
            if (!operacao.equals("INCLUSAO")) {
                var evento = em.find(EventoOperacional.class, dados.eventoId());
                evento.setEquipeDesignada(em.find(br.com.powergrid.controlcenter.model.EquipeManutencao.class, dados.equipeAId()));
                evento.setVersaoDesignacao(1);
                var h = new HistoricoDesignacaoEquipe();
                h.setEventoOperacional(evento); h.setEquipeNova(evento.getEquipeDesignada());
                h.setUsuario(em.find(Usuario.class, dados.operadorId())); h.setVersaoDesignacao(1);
                h.setRegistradoEm(clock.instant().minusSeconds(60).atOffset(ZoneOffset.UTC)); h.setComentario("Designação anterior");
                em.persist(h); em.flush();
            }
            return dados;
        });
        int anterior = operacao.equals("INCLUSAO") ? 0 : 1;
        Long destino = operacao.equals("RETIRADA") ? null : operacao.equals("INCLUSAO") ? d.equipeAId() : d.equipeBId();
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, d.operadorId());
            EventosTransicaoFixture.autenticar(email);
            doAnswer(invocation -> {
                var h = invocation.getArgument(0, HistoricoDesignacaoEquipe.class);
                assertThat(jdbc.queryForObject("SELECT equipe_designada_id FROM evento_operacional WHERE id = ?", Long.class, d.eventoId()))
                        .isEqualTo(destino);
                assertThat(jdbc.queryForObject("SELECT versao_designacao FROM evento_operacional WHERE id = ?", Integer.class, d.eventoId()))
                        .isEqualTo(anterior + 1);
                String sql = "INSERT INTO historico_designacao_equipe (evento_operacional_id, equipe_anterior_id, equipe_nova_id,"
                        + " usuario_id, registrado_em, comentario, versao_designacao) VALUES (?, ?, ?, ?, ?, ?, ?)";
                Object[] parametros = {d.eventoId(), h.getEquipeAnterior() == null ? null : h.getEquipeAnterior().getId(),
                        destino, d.operadorId(), h.getRegistradoEm(), h.getComentario(), h.getVersaoDesignacao()};
                jdbc.update(sql, parametros);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_designacao_equipe WHERE evento_operacional_id = ?",
                        Long.class, d.eventoId())).isEqualTo(anterior + 1L);
                jdbc.update(sql, parametros);
                return h;
            }).when(historicos).saveAndFlush(any(HistoricoDesignacaoEquipe.class));

            assertThrows(DataIntegrityViolationException.class,
                    () -> service.designar(d.eventoId(), destino, anterior, "Operação revertida", d.operadorId()));
            verify(historicos).saveAndFlush(any(HistoricoDesignacaoEquipe.class));
            assertThat(jdbc.queryForObject("SELECT equipe_designada_id FROM evento_operacional WHERE id = ?", Long.class, d.eventoId()))
                    .isEqualTo(anterior == 0 ? null : d.equipeAId());
            assertThat(jdbc.queryForObject("SELECT versao_designacao FROM evento_operacional WHERE id = ?", Integer.class, d.eventoId()))
                    .isEqualTo(anterior);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_designacao_equipe WHERE evento_operacional_id = ?",
                    Long.class, d.eventoId())).isEqualTo((long) anterior);
            if (anterior == 1) assertThat(jdbc.queryForObject(
                    "SELECT comentario FROM historico_designacao_equipe WHERE evento_operacional_id = ?", String.class, d.eventoId()))
                    .isEqualTo("Designação anterior");
        } finally {
            tx.executeWithoutResult(status -> DesignacaoEquipeFixture.limpar(jdbc, d));
        }
    }
}
