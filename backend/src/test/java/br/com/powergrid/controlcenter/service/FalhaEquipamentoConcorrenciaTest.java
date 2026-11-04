package br.com.powergrid.controlcenter.service;

import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.AlimentadorRequestDTO;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.Subestacao;
import org.junit.jupiter.api.Test;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.support.*;
import jakarta.persistence.EntityManager;
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
class FalhaEquipamentoConcorrenciaTest {
    @Autowired private FalhaEquipamentoService service;
    @Autowired private SubestacaoService subestacoes;
    @Autowired private AlimentadorService alimentadores;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;

    @ParameterizedTest
    @ValueSource(strings = {"REGISTRO", "PUT"})
    void deveSerializarRegistroDuplicadoOuPutComEstadoAnteriorEmCache(String concorrente) throws Exception {
        var tx = new TransactionTemplate(manager);
        var d = tx.execute(status -> FalhaEquipamentoFixture.criar(em));
        var executor = Executors.newFixedThreadPool(2);
        var bloqueado = new CountDownLatch(1); var liberar = new CountDownLatch(1); var carregado = new CountDownLatch(1);
        try {
            var primeiro = executor.submit(() -> {
                EventosTransicaoFixture.autenticar(d.email());
                try { return tx.execute(status -> {
                    service.registrar(d.ativoId(), d.usuarioId(), "Primeiro registro", null);
                    em.flush(); bloqueado.countDown(); aguardar(liberar); return 201;
                }); } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var segundo = executor.submit(() -> {
                EventosTransicaoFixture.autenticar(d.email());
                try {
                    return tx.execute(status -> {
                        assertThat(em.find(AtivoRede.class, d.ativoId()).getEstadoOperacional()).isEqualTo(AtivoRede.EstadoOperacional.OPERACIONAL);
                        carregado.countDown();
                        if (concorrente.equals("REGISTRO")) service.registrar(d.ativoId(), d.usuarioId(), "Segundo registro", null);
                        else subestacoes.atualizar(d.ativoId(), new SubestacaoRequestDTO("Atualização concorrente", BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2026, 1, 1), null));
                        return 200;
                    });
                } catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(carregado.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> segundo.get(150, TimeUnit.MILLISECONDS));
            liberar.countDown();
            assertThat(List.of(primeiro.get(30, TimeUnit.SECONDS), segundo.get(30, TimeUnit.SECONDS))).containsExactly(201, 409);
            assertThat(jdbc.queryForObject("SELECT estado_operacional FROM ativo_rede WHERE id = ?", String.class, d.ativoId())).isEqualTo("FALHA");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_operacional WHERE ativo_id = ?", Long.class, d.ativoId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM falha_equipamento f JOIN evento_operacional e ON e.id=f.evento_operacional_id WHERE e.ativo_id = ?", Long.class, d.ativoId())).isEqualTo(1);
        } finally {
            liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> FalhaEquipamentoFixture.limpar(jdbc, d));
        }
    }

    @Test
    void trocasTopologicasCruzadasDevemSeguirOrdemGlobalDeLocksSemDeadlock() throws Exception {
        var tx = new TransactionTemplate(manager);
        var a = tx.execute(status -> FalhaEquipamentoFixture.criar(em));
        var b = tx.execute(status -> FalhaEquipamentoFixture.criar(em));
        var filhos = tx.execute(status -> {
            var um = new Alimentador(); um.setNome("Alimentador A"); um.setEstadoOperacional(AtivoRede.EstadoOperacional.OPERACIONAL);
            um.setSubestacao(em.find(Subestacao.class, a.ativoId())); em.persist(um);
            var dois = new Alimentador(); dois.setNome("Alimentador B"); dois.setEstadoOperacional(AtivoRede.EstadoOperacional.OPERACIONAL);
            dois.setSubestacao(em.find(Subestacao.class, b.ativoId())); em.persist(dois); em.flush();
            return List.of(um.getId(), dois.getId());
        });
        var executor = Executors.newFixedThreadPool(2); var iniciar = new CountDownLatch(1);
        try {
            var primeiro = executor.submit(() -> { aguardar(iniciar); alimentadores.atualizar(filhos.get(0), parametro(b.ativoId())); return 200; });
            var segundo = executor.submit(() -> { aguardar(iniciar); alimentadores.atualizar(filhos.get(1), parametro(a.ativoId())); return 200; });
            iniciar.countDown();
            assertThat(List.of(primeiro.get(30, TimeUnit.SECONDS), segundo.get(30, TimeUnit.SECONDS))).containsExactly(200, 200);
            assertThat(jdbc.queryForObject("SELECT subestacao_id FROM alimentador WHERE id = ?", Long.class, filhos.get(0))).isEqualTo(b.ativoId());
            assertThat(jdbc.queryForObject("SELECT subestacao_id FROM alimentador WHERE id = ?", Long.class, filhos.get(1))).isEqualTo(a.ativoId());
        } finally {
            iniciar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> {
                for (Long id : filhos) { jdbc.update("DELETE FROM alimentador WHERE id = ?", id); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id); }
                FalhaEquipamentoFixture.limpar(jdbc, a); FalhaEquipamentoFixture.limpar(jdbc, b);
            });
        }
    }

    private AlimentadorRequestDTO parametro(Long subestacaoId) {
        return new AlimentadorRequestDTO("Alimentador atualizado", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                subestacaoId, LocalDate.of(2026, 1, 1), AtivoRede.EstadoOperacional.OPERACIONAL);
    }

    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo limite da sincronização."); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
