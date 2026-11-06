package br.com.powergrid.controlcenter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import br.com.powergrid.controlcenter.service.AlertaComunicacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;

class AgendamentoComunicacaoTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AgendamentoComunicacao.class)
            .withBean(AlertaComunicacaoService.class, () -> mock(AlertaComunicacaoService.class));

    @Test
    void ficaDesabilitadoPorPadrao() {
        context.run(app -> assertThat(app).doesNotHaveBean(AgendamentoComunicacao.class)
                .doesNotHaveBean(ScheduledTaskHolder.class));
    }

    @Test
    void chamaMotorAutomaticamenteQuandoHabilitado() {
        context.withPropertyValues("scada.monitoramento.comunicacao.enabled=true",
                        "scada.monitoramento.comunicacao.fixed-delay=60000")
                .run(app -> {
                    assertThat(app).hasSingleBean(AgendamentoComunicacao.class);
                    assertThat(app.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(1);
                    verify(app.getBean(AlertaComunicacaoService.class), timeout(5000)).avaliar();
                });
    }
}
