package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.repository.AlertaComunicacaoRepository;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertaComunicacaoConfiguracaoTest {
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarTimeoutNaoPositivo(int timeout) {
        assertThatThrownBy(() -> new AlertaComunicacaoService(null, null, Clock.systemUTC(), null, timeout))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeout-segundos");
    }

    @Test
    void deveAplicarTimeoutConfigurado() {
        var alertas = mock(AlertaComunicacaoRepository.class);
        var fontes = mock(FonteMedicaoRepository.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(fontes.maiorId()).thenReturn(1L);
        when(fontes.buscarIdsParaAvaliacao(eq(0L), eq(1L), any(Pageable.class))).thenReturn(List.of(1L));
        when(fontes.buscarIdsParaAvaliacao(eq(1L), eq(1L), any(Pageable.class))).thenReturn(List.of());
        var fonte = new FonteMedicao(); fonte.setId(1L);
        fonte.setMonitoradaDesde(OffsetDateTime.parse("2026-10-31T11:59:49Z"));
        when(fontes.buscarPorIdComBloqueio(1L)).thenReturn(Optional.of(fonte));
        var clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
        var service = new AlertaComunicacaoService(alertas, fontes, clock, manager, 10);
        var resultado = service.avaliar();
        assertThat(resultado.toleranciaSegundos()).isEqualTo(10);
        assertThat(resultado.alertasCriados()).isEqualTo(1);
        verify(alertas).saveAndFlush(argThat(a -> a.getToleranciaSegundos() == 10));
    }
}
