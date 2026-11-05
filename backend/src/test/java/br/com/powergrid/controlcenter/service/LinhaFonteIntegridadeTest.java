package br.com.powergrid.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import br.com.powergrid.controlcenter.dto.FonteMedicaoRequestDTO;
import br.com.powergrid.controlcenter.dto.LinhaTransmissaoRequestDTO;
import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.model.OrigemMedicao;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class LinhaFonteIntegridadeTest {
    private static final LocalDate DATA_INSTALACAO = LocalDate.of(2025, 1, 1);

    @Autowired private LinhaTransmissaoService linhas;
    @Autowired private SubestacaoService subestacoes;
    @Autowired private FonteMedicaoService fontes;
    @Autowired private FonteMedicaoRepository fonteRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private Long linhaId;
    private Long origem;
    private Long destino;
    private Long substituta;

    @BeforeEach
    void prepararLinha() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            origem = criarSubestacao("Origem");
            destino = criarSubestacao("Destino");
            substituta = criarSubestacao("Substituta");
            linhaId = linhas.criar(linha(origem, destino)).id();
        });
    }

    @AfterEach
    void limparLinha() {
        if (linhaId == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id = ?", linhaId);
            jdbc.update("DELETE FROM linha_transmissao WHERE id = ?", linhaId);
            jdbc.update("DELETE FROM ativo_rede WHERE id = ?", linhaId);
            for (Long id : List.of(origem, destino, substituta)) {
                jdbc.update("DELETE FROM subestacao WHERE id = ?", id);
                jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejeitaRemocaoDeQualquerExtremidadeReferenciada(boolean referenciaOrigem) {
        Long referencia = referenciaOrigem ? origem : destino;
        fontes.criar(linhaId, fonte(referencia));
        var atualizacao = referenciaOrigem ? linha(substituta, destino) : linha(origem, substituta);

        var erro = assertThrows(ResponseStatusException.class, () -> linhas.atualizar(linhaId, atualizacao));

        assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(linhas.buscarPorId(linhaId).subestacaoOrigemId()).isEqualTo(origem);
        assertThat(linhas.buscarPorId(linhaId).subestacaoDestinoId()).isEqualTo(destino);
        assertThat(fonteRepository.existsByAtivoIdAndSubestacaoReferenciaId(linhaId, referencia)).isTrue();
    }

    @Test
    void permiteInverterExtremidadesESubstituirAquelaSemReferencia() {
        fontes.criar(linhaId, fonte(origem));

        assertThat(linhas.atualizar(linhaId, linha(destino, origem)).subestacaoDestinoId()).isEqualTo(origem);
        assertThat(linhas.atualizar(linhaId, linha(substituta, origem)).subestacaoOrigemId()).isEqualTo(substituta);
        assertThat(fonteRepository.existsByAtivoIdAndSubestacaoReferenciaId(linhaId, origem)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void serializaCadastroDeFonteEAlteracaoTopologica(boolean fontePrimeiro) throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var primeiraOperacaoExecutada = new CountDownLatch(1);
        var segundaOperacaoIniciada = new CountDownLatch(1);
        var confirmarPrimeira = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var requestFonte = fonte(origem);
        var atualizacao = linha(substituta, destino);
        Runnable criarFonte = () -> fontes.criar(linhaId, requestFonte);
        Runnable editarLinha = () -> linhas.atualizar(linhaId, atualizacao);
        Runnable primeiraOperacao = fontePrimeiro ? criarFonte : editarLinha;
        Runnable segundaOperacao = fontePrimeiro ? editarLinha : criarFonte;

        try {
            var primeira = executor.submit(() -> tx.executeWithoutResult(status -> {
                primeiraOperacao.run();
                primeiraOperacaoExecutada.countDown();
                aguardar(confirmarPrimeira);
            }));
            aguardar(primeiraOperacaoExecutada);
            var segunda = executor.submit(() -> assertThrows(ResponseStatusException.class,
                    () -> tx.executeWithoutResult(status -> {
                        linhas.buscarPorId(linhaId); // Carrega a topologia anterior no contexto da transação.
                        segundaOperacaoIniciada.countDown();
                        segundaOperacao.run();
                    })));
            aguardar(segundaOperacaoIniciada);
            assertThrows(TimeoutException.class, () -> segunda.get(150, TimeUnit.MILLISECONDS));
            confirmarPrimeira.countDown();

            primeira.get(30, TimeUnit.SECONDS);
            assertThat(segunda.get(30, TimeUnit.SECONDS).getStatusCode())
                    .isEqualTo(fontePrimeiro ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
            assertThat(linhas.buscarPorId(linhaId).subestacaoOrigemId()).isEqualTo(fontePrimeiro ? origem : substituta);
            assertThat(fonteRepository.findByAtivoIdOrderByIdAsc(linhaId)).hasSize(fontePrimeiro ? 1 : 0);
            assertThat(fonteRepository.existsByAtivoIdAndSubestacaoReferenciaId(linhaId, origem)).isEqualTo(fontePrimeiro);
        } finally {
            confirmarPrimeira.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long criarSubestacao(String nome) {
        return subestacoes.criar(new SubestacaoRequestDTO(nome, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.TEN, DATA_INSTALACAO, null)).id();
    }

    private LinhaTransmissaoRequestDTO linha(Long origemId, Long destinoId) {
        return new LinhaTransmissaoRequestDTO("Linha para medição", null, null, BigDecimal.TEN,
                origemId, destinoId, BigDecimal.TEN, DATA_INSTALACAO, null);
    }

    private FonteMedicaoRequestDTO fonte(Long referencia) {
        return new FonteMedicaoRequestDTO(UUID.randomUUID().toString(), OrigemMedicao.SIMULADA, referencia);
    }

    private void aguardar(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
