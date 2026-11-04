package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture;
import br.com.powergrid.controlcenter.support.TratativaAlertaFixture.Dados;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
class TratativaAlertaConcorrenciaTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00.123456Z");
    @Autowired private TratativaAlertaService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;

    @BeforeEach
    void prepararRelogio() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duasAcoesIguaisConcorrentesDevemPreservarPrimeiroAutorEUmUnicoRegistro(boolean resolver) throws Exception {
        var d = preparar(resolver);
        var destino = resolver ? "RESOLVIDO" : "RECONHECIDO";
        try {
            var resultados = disputar(d, () -> {
                agir(resolver, d, d.operadorId(), "Primeira ação"); return 200;
            }, () -> {
                agir(resolver, d, d.gerenteId(), "Repetição por outro usuário"); return 200;
            });
            assertThat(resultados).containsExactly(200, 200);
            assertThat(situacao(d)).isEqualTo(destino);
            assertThat(total(d)).isEqualTo(resolver ? 2 : 1);
            assertThat(jdbc.queryForObject("SELECT usuario_id FROM historico_alerta WHERE alerta_id = ? AND situacao_nova = ?",
                    Long.class, d.alertaId(), destino)).isEqualTo(d.operadorId());
            assertThat(jdbc.queryForObject("SELECT comentario FROM historico_alerta WHERE alerta_id = ? AND situacao_nova = ?",
                    String.class, d.alertaId(), destino)).isEqualTo("Primeira ação");
            assertThat(jdbc.queryForObject("SELECT alterado_em FROM historico_alerta WHERE alerta_id = ? AND situacao_nova = ?",
                    OffsetDateTime.class, d.alertaId(), destino).toInstant()).isEqualTo(AGORA);
        } finally { limpar(d); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveSerializarReconhecimentoEResolucaoSemPermitirSalto(boolean reconhecerPrimeiro) throws Exception {
        var d = preparar(false);
        try {
            Supplier<Integer> primeira = () -> {
                agir(!reconhecerPrimeiro, d, d.operadorId(), "Primeira ação"); return 200;
            };
            Supplier<Integer> segunda = () -> {
                agir(reconhecerPrimeiro, d, d.gerenteId(), "Segunda ação"); return 200;
            };
            var resultado = disputar(d, primeira, segunda);
            assertThat(resultado).containsExactly(reconhecerPrimeiro ? 200 : 409, 200);
            assertThat(situacao(d)).isEqualTo(reconhecerPrimeiro ? "RESOLVIDO" : "RECONHECIDO");
            assertThat(total(d)).isEqualTo(reconhecerPrimeiro ? 2 : 1);
            assertThat(jdbc.queryForObject("SELECT usuario_id FROM historico_alerta WHERE alerta_id = ? AND situacao_nova = 'RECONHECIDO'",
                    Long.class, d.alertaId())).isEqualTo(reconhecerPrimeiro ? d.operadorId() : d.gerenteId());
        } finally { limpar(d); }
    }

    @Test
    void bloqueioDeUmAlertaNaoDeveImpedirTratativaDeOutroAlerta() throws Exception {
        var a = preparar(false); var b = preparar(false);
        var executor = Executors.newFixedThreadPool(2);
        var bloqueado = new CountDownLatch(1); var liberar = new CountDownLatch(1);
        try {
            var primeira = executor.submit(() -> comoUsuario(a.operadorId(), () -> new TransactionTemplate(manager).execute(status -> {
                service.reconhecer(a.alertaId(), a.operadorId(), "Alerta A");
                bloqueado.countDown(); aguardar(liberar); return 200;
            })));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var segunda = executor.submit(() -> comoUsuario(b.gerenteId(), () -> {
                service.reconhecer(b.alertaId(), b.gerenteId(), "Alerta B"); return 200;
            }));
            assertThat(segunda.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            liberar.countDown(); assertThat(primeira.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(total(a)).isEqualTo(1); assertThat(total(b)).isEqualTo(1);
        } finally {
            liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            limpar(b); limpar(a);
        }
    }

    private List<Integer> disputar(Dados d, Supplier<Integer> primeiro, Supplier<Integer> segundo) throws Exception {
        var bloqueado = new CountDownLatch(1); var liberar = new CountDownLatch(1); var iniciado = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> comoUsuario(d.operadorId(), () -> new TransactionTemplate(manager).execute(status -> {
                int resultado;
                try { resultado = primeiro.get(); }
                catch (ResponseStatusException ex) { status.setRollbackOnly(); resultado = ex.getStatusCode().value(); }
                bloqueado.countDown(); aguardar(liberar); return resultado;
            })));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var b = executor.submit(() -> comoUsuario(d.gerenteId(), () -> {
                iniciado.countDown();
                try { return segundo.get(); }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            }));
            assertThat(iniciado.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> b.get(150, TimeUnit.MILLISECONDS));
            liberar.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void agir(boolean resolver, Dados d, Long usuario, String comentario) {
        if (resolver) service.resolver(d.alertaId(), usuario, comentario);
        else service.reconhecer(d.alertaId(), usuario, comentario);
    }
    private int comoUsuario(Long id, Supplier<Integer> operacao) {
        EventosTransicaoFixture.autenticar(jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, id));
        try { return operacao.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private Dados preparar(boolean reconhecido) {
        return new TransactionTemplate(manager).execute(status -> {
            var d = TratativaAlertaFixture.criar(em, reconhecido ? SituacaoAlerta.RECONHECIDO : SituacaoAlerta.ABERTO, false);
            if (reconhecido) TratativaAlertaFixture.registrarReconhecimento(em, d);
            return d;
        });
    }
    private void limpar(Dados d) { new TransactionTemplate(manager).executeWithoutResult(status -> TratativaAlertaFixture.limpar(jdbc, d)); }
    private String situacao(Dados d) { return jdbc.queryForObject("SELECT situacao FROM alerta WHERE id = ?", String.class, d.alertaId()); }
    private long total(Dados d) { return jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta WHERE alerta_id = ?", Long.class, d.alertaId()); }
    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo limite de sincronização."); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }
}
