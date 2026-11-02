package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.MedicaoEnergiaRequestDTO;
import br.com.powergrid.controlcenter.support.EnergiaAlimentadorFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest
class MedicaoEnergiaRollbackTest {
    @Autowired private MedicaoEnergiaService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private FonteMedicaoService comunicacao;

    @Test
    void falhaPosteriorAoRestabelecimentoDeveDesfazerMedicaoContatoEAlerta() {
        var tx = new TransactionTemplate(manager); Long[] ids = tx.execute(status -> EnergiaAlimentadorFixture.criar(em));
        var agora = OffsetDateTime.now(ZoneOffset.UTC); EnergiaAlimentadorFixture.autenticar();
        try {
            FonteMedicaoService alvo = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(comunicacao);
            doAnswer(invocation -> {
                invocation.callRealMethod();
                assertThat(jdbc.queryForObject("select status from alerta_comunicacao where id = ?", String.class, ids[4])).isEqualTo("RESTABELECIDO");
                assertThat(jdbc.queryForObject("select ultima_comunicacao_em from fonte_medicao where id = ?", OffsetDateTime.class, ids[2])).isNotNull();
                throw new IllegalStateException("Falha simulada após confirmação de contato");
            }).when(alvo).registrarComunicacaoValida(eq(ids[2]), any(OffsetDateTime.class));
            assertThrows(IllegalStateException.class, () -> service.ingerir(ids[0], ids[2],
                    new MedicaoEnergiaRequestDTO(agora.minusHours(2), agora.minusHours(1), BigDecimal.TEN)));
            assertThat(jdbc.queryForObject("select count(*) from medicao_energia_alimentador where fonte_id = ?", Long.class, ids[2])).isZero();
            assertThat(jdbc.queryForObject("select ultima_comunicacao_em from fonte_medicao where id = ?", OffsetDateTime.class, ids[2])).isNull();
            assertThat(jdbc.queryForObject("select status from alerta_comunicacao where id = ?", String.class, ids[4])).isEqualTo("ATIVO");
            assertThat(jdbc.queryForObject("select restabelecido_em from alerta_comunicacao where id = ?", OffsetDateTime.class, ids[4])).isNull();
        } finally {
            SecurityContextHolder.clearContext();
            tx.executeWithoutResult(status -> EnergiaAlimentadorFixture.limpar(jdbc, ids));
        }
    }
}
