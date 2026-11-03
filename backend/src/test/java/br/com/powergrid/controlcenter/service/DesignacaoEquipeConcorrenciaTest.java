package br.com.powergrid.controlcenter.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.*;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture;
import br.com.powergrid.controlcenter.support.DesignacaoEquipeFixture.Dados;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class DesignacaoEquipeConcorrenciaTest {
    @Autowired private DesignacaoEquipeService service;
    @Autowired private EquipeManutencaoService manutencao;
    @Autowired private EventoOperacionalService eventos;
    @Autowired private EventoOperacionalRepository eventosRepository;
    @Autowired private EquipeManutencaoRepository equipes;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;

    @Test
    void deveConfirmarSomenteUmaDesignacaoComMesmaVersaoEsperada() throws Exception {
        var d = preparar();
        try {
            var resultado = disputar(d, () -> {
                eventosRepository.buscarPorIdParaDesignacao(d.eventoId()).orElseThrow();
                service.designar(d.eventoId(), d.equipeAId(), 0, "Primeiro comando", d.gerenteId()); return 200;
            }, () -> {
                service.designar(d.eventoId(), d.equipeBId(), 0, "Comando desatualizado", d.gerenteId()); return 200;
            });
            assertThat(resultado).containsExactly(200, 409);
            assertThat(versao(d)).isEqualTo(1);
            assertThat(equipeAtual(d)).isEqualTo(d.equipeAId());
            assertThat(totalHistorico(d)).isEqualTo(1);
        } finally { limpar(d); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveSerializarDesignacaoEEncerramentoDeVigenciaSemDeadlock(boolean designarPrimeiro) throws Exception {
        var d = preparar();
        try {
            Supplier<Integer> designar = () -> {
                service.designar(d.eventoId(), d.equipeAId(), 0, "Equipe designada", d.gerenteId()); return 200;
            };
            Supplier<Integer> encerrar = () -> {
                equipes.buscarPorIdParaAlteracao(d.equipeAId()).orElseThrow();
                manutencao.atualizarSituacao(d.equipeAId(), SituacaoEquipe.FORA_DE_SERVICO);
                manutencao.removerIntegrante(d.equipeAId(), d.tecnicoId()); return 204;
            };
            var resultado = disputar(d, designarPrimeiro ? designar : encerrar, designarPrimeiro ? encerrar : designar);
            assertThat(resultado).containsExactly(designarPrimeiro ? 200 : 204, designarPrimeiro ? 204 : 409);
            assertThat(versao(d)).isEqualTo(designarPrimeiro ? 1 : 0);
            assertThat(equipeAtual(d)).isEqualTo(designarPrimeiro ? d.equipeAId() : null);
            assertThat(totalHistorico(d)).isEqualTo(designarPrimeiro ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculo_equipe WHERE equipe_id = ? AND fim_vigencia IS NULL",
                    Long.class, d.equipeAId())).isZero();
        } finally { limpar(d); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveSerializarDesignacaoEConclusaoDoEvento(boolean designarPrimeiro) throws Exception {
        var d = preparar();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var evento = em.find(EventoOperacional.class, d.eventoId());
            evento.setSituacao("EM_ANDAMENTO");
            evento.setInicioEfetivo(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        });
        try {
            Supplier<Integer> designar = () -> {
                service.designar(d.eventoId(), d.equipeAId(), 0, "Atendimento", d.gerenteId()); return 200;
            };
            Supplier<Integer> concluir = () -> {
                eventos.transicionar(d.eventoId(), SituacaoEventoOperacional.EM_ANDAMENTO,
                        SituacaoEventoOperacional.CONCLUIDO, "Concluído"); return 201;
            };
            var resultado = disputar(d, designarPrimeiro ? designar : concluir, designarPrimeiro ? concluir : designar);
            assertThat(resultado).containsExactly(designarPrimeiro ? 200 : 201, designarPrimeiro ? 201 : 409);
            assertThat(jdbc.queryForObject("SELECT situacao FROM evento_operacional WHERE id = ?", String.class, d.eventoId()))
                    .isEqualTo("CONCLUIDO");
            assertThat(versao(d)).isEqualTo(designarPrimeiro ? 1 : 0);
            assertThat(totalHistorico(d)).isEqualTo(designarPrimeiro ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional WHERE evento_id = ?", Long.class, d.eventoId()))
                    .isEqualTo(1);
        } finally { limpar(d); }
    }

    @Test
    void devePermitirMesmaEquipeEmEventosDistintosSemReservarCapacidade() throws Exception {
        var a = preparar(); var b = preparar();
        try {
            var resultado = disputar(a, () -> {
                service.designar(a.eventoId(), a.equipeAId(), 0, "Atendimento A", a.gerenteId()); return 200;
            }, () -> {
                service.designar(b.eventoId(), a.equipeAId(), 0, "Atendimento B", a.gerenteId()); return 200;
            });
            assertThat(resultado).containsExactly(200, 200);
            assertThat(equipeAtual(a)).isEqualTo(a.equipeAId());
            assertThat(equipeAtual(b)).isEqualTo(a.equipeAId());
            assertThat(totalHistorico(a)).isEqualTo(1);
            assertThat(totalHistorico(b)).isEqualTo(1);
        } finally { limpar(b); limpar(a); }
    }

    @Test
    void substituicoesCruzadasNaoDevemBloquearEquipesAnterioresEmOrdemInversa() throws Exception {
        var a = preparar(); var b = preparar();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            em.find(EventoOperacional.class, a.eventoId()).setEquipeDesignada(em.find(EquipeManutencao.class, a.equipeAId()));
            em.find(EventoOperacional.class, b.eventoId()).setEquipeDesignada(em.find(EquipeManutencao.class, a.equipeBId()));
        });
        var executor = Executors.newFixedThreadPool(2);
        var iniciar = new CountDownLatch(1);
        try {
            var primeira = executor.submit(() -> comoGerente(a, () -> {
                aguardar(iniciar); service.designar(a.eventoId(), a.equipeBId(), 0, "Troca A", a.gerenteId()); return 200;
            }));
            var segunda = executor.submit(() -> comoGerente(a, () -> {
                aguardar(iniciar); service.designar(b.eventoId(), a.equipeAId(), 0, "Troca B", a.gerenteId()); return 200;
            }));
            iniciar.countDown();
            assertThat(List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS))).containsExactly(200, 200);
            assertThat(equipeAtual(a)).isEqualTo(a.equipeBId());
            assertThat(equipeAtual(b)).isEqualTo(a.equipeAId());
            assertThat(versao(a)).isEqualTo(1); assertThat(versao(b)).isEqualTo(1);
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            limpar(b); limpar(a);
        }
    }

    private List<Integer> disputar(Dados d, Supplier<Integer> primeiro, Supplier<Integer> segundo) throws Exception {
        var bloqueado = new CountDownLatch(1); var liberar = new CountDownLatch(1); var segundoIniciado = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> comoGerente(d, () -> new TransactionTemplate(manager).execute(status -> {
                int resultado = primeiro.get(); em.flush(); bloqueado.countDown(); aguardar(liberar); return resultado;
            })));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var b = executor.submit(() -> comoGerente(d, () -> {
                segundoIniciado.countDown();
                try { return new TransactionTemplate(manager).execute(status -> segundo.get()); }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            }));
            assertThat(segundoIniciado.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> b.get(150, TimeUnit.MILLISECONDS));
            liberar.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int comoGerente(Dados d, Supplier<Integer> acao) {
        String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, d.gerenteId());
        EventosTransicaoFixture.autenticar(email);
        try { return acao.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private Dados preparar() {
        return new TransactionTemplate(manager).execute(status -> DesignacaoEquipeFixture.criar(em));
    }
    private void limpar(Dados d) {
        new TransactionTemplate(manager).executeWithoutResult(status -> DesignacaoEquipeFixture.limpar(jdbc, d));
    }
    private int versao(Dados d) {
        return jdbc.queryForObject("SELECT versao_designacao FROM evento_operacional WHERE id = ?", Integer.class, d.eventoId());
    }
    private Long equipeAtual(Dados d) {
        return jdbc.queryForObject("SELECT equipe_designada_id FROM evento_operacional WHERE id = ?", Long.class, d.eventoId());
    }
    private long totalHistorico(Dados d) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM historico_designacao_equipe WHERE evento_operacional_id = ?", Long.class, d.eventoId());
    }
    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo limite de sincronização."); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
