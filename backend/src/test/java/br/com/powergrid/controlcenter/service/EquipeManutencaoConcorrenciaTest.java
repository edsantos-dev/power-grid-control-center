package br.com.powergrid.controlcenter.service;

import java.util.concurrent.*;
import java.util.function.Supplier;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class EquipeManutencaoConcorrenciaTest {
    @Autowired private EquipeManutencaoService service;
    @Autowired private EquipeManutencaoRepository equipes;
    @Autowired private VinculoEquipeRepository vinculos;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duasInclusoesConcorrentesDevemCriarSomenteUmVinculoAberto() throws Exception {
        Long[] ids = preparar(false);
        try {
            var resultado = disputar(ids[0], () -> service.adicionarIntegrante(ids[0], ids[1]).criado() ? 201 : 200,
                    () -> service.adicionarIntegrante(ids[0], ids[1]).criado() ? 201 : 200);
            assertThat(resultado).containsExactly(201, 200);
            assertThat(vinculos.countByEquipeIdAndFimVigenciaIsNull(ids[0])).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from vinculo_equipe where equipe_id = ?", Long.class, ids[0])).isEqualTo(1);
        } finally { limpar(ids[0]); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveSerializarRemocaoDoUltimoIntegranteEAtivacaoDaEquipe(boolean ativarPrimeiro) throws Exception {
        Long[] ids = preparar(true);
        try {
            Supplier<Integer> ativar = () -> { service.atualizarSituacao(ids[0], SituacaoEquipe.DISPONIVEL); return 200; };
            Supplier<Integer> remover = () -> { service.removerIntegrante(ids[0], ids[1]); return 204; };
            var resultado = disputar(ids[0], ativarPrimeiro ? ativar : remover, ativarPrimeiro ? remover : ativar);
            assertThat(resultado).containsExactly(ativarPrimeiro ? 200 : 204, 409);
            assertThat(equipes.findById(ids[0]).orElseThrow().getSituacao())
                    .isEqualTo(ativarPrimeiro ? SituacaoEquipe.DISPONIVEL : SituacaoEquipe.FORA_DE_SERVICO);
            assertThat(vinculos.countByEquipeIdAndFimVigenciaIsNull(ids[0])).isEqualTo(ativarPrimeiro ? 1 : 0);
        } finally { limpar(ids[0]); }
    }

    @Test
    void duasRemocoesConcorrentesDevemPreservarUltimoIntegranteDeEquipeDisponivel() throws Exception {
        Long[] ids = preparar(true);
        comoGestor(() -> {
            service.adicionarIntegrante(ids[0], ids[2]);
            service.atualizarSituacao(ids[0], SituacaoEquipe.DISPONIVEL);
            return 0;
        });
        try {
            var resultado = disputar(ids[0], () -> { service.removerIntegrante(ids[0], ids[1]); return 204; },
                    () -> { service.removerIntegrante(ids[0], ids[2]); return 204; });
            assertThat(resultado).containsExactly(204, 409);
            assertThat(vinculos.countByEquipeIdAndFimVigenciaIsNull(ids[0])).isEqualTo(1);
        } finally { limpar(ids[0]); }
    }

    private java.util.List<Integer> disputar(Long equipeId, Supplier<Integer> primeiro, Supplier<Integer> segundo) throws Exception {
        var bloqueado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var segundoIniciado = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> comoGestor(() -> new TransactionTemplate(transactionManager).execute(status -> {
                equipes.buscarPorIdParaAlteracao(equipeId).orElseThrow();
                int resultado = primeiro.get();
                em.flush();
                bloqueado.countDown();
                aguardar(liberar);
                return resultado;
            })));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var b = executor.submit(() -> comoGestor(() -> {
                segundoIniciado.countDown();
                try { return segundo.get(); }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            }));
            assertThat(segundoIniciado.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> b.get(150, TimeUnit.MILLISECONDS));
            liberar.countDown();
            return java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long[] preparar(boolean integrante) {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            var equipe = new EquipeManutencao(); equipe.setNome("Concorrência US04"); equipe.setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
            equipes.saveAndFlush(equipe);
            var usuarios = em.createQuery("select u.id from Usuario u order by u.id", Long.class).setMaxResults(2).getResultList();
            return new Long[] {equipe.getId(), usuarios.get(0), usuarios.get(1)};
        });
        if (integrante) comoGestor(() -> { service.adicionarIntegrante(ids[0], ids[1]); return 0; });
        return ids;
    }

    private int comoGestor(Supplier<Integer> operacao) {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("gerente", "",
                AuthorityUtils.createAuthorityList("ROLE_GERENTE_OPERACOES")));
        SecurityContextHolder.setContext(contexto);
        try { return operacao.get(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo limite de sincronização.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(ex);
        }
    }

    private void limpar(Long equipeId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from vinculo_equipe where equipe_id = ?", equipeId);
            jdbc.update("delete from equipe_manutencao where id = ?", equipeId);
        });
    }
}
