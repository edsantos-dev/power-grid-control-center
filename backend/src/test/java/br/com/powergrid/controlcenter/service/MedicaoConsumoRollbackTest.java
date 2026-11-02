package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.dto.MedicaoConsumoRequestDTO;
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture;
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
class MedicaoConsumoRollbackTest {
    @Autowired private MedicaoConsumoService service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private FonteMedicaoService comunicacao;

    @Test
    void falhaDepoisDeRestabelecerDeveReverterConsumoContatoEAlerta() {
        var tx = new TransactionTemplate(manager); var dados = tx.execute(status -> ConsumoMedidorFixture.criar(em, true));
        ConsumoMedidorFixture.autenticar();
        try {
            FonteMedicaoService alvo = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(comunicacao);
            doAnswer(invocation -> {
                invocation.callRealMethod();
                assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE id = ?", String.class, dados.alertaId())).isEqualTo("RESTABELECIDO");
                assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, dados.fonteId())).isNotNull();
                throw new IllegalStateException("Falha simulada após restabelecer comunicação");
            }).when(alvo).registrarComunicacaoValida(eq(dados.fonteId()), any(OffsetDateTime.class));
            assertThrows(IllegalStateException.class, () -> service.ingerir(dados.medidorId(), new MedicaoConsumoRequestDTO(
                    dados.fonteId(), OffsetDateTime.parse("2026-01-02T10:00:00Z"), OffsetDateTime.parse("2026-01-02T11:00:00Z"), BigDecimal.TEN)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medicao_consumo_medidor WHERE fonte_id = ?", Long.class, dados.fonteId())).isZero();
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = ?", OffsetDateTime.class, dados.fonteId())).isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM alerta_comunicacao WHERE id = ?", String.class, dados.alertaId())).isEqualTo("ATIVO");
            assertThat(jdbc.queryForObject("SELECT restabelecido_em FROM alerta_comunicacao WHERE id = ?", OffsetDateTime.class, dados.alertaId())).isNull();
        } finally {
            SecurityContextHolder.clearContext(); tx.executeWithoutResult(status -> ConsumoMedidorFixture.limpar(jdbc, dados));
        }
    }
}
