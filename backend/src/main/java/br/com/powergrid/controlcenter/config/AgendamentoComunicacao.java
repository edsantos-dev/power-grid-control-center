package br.com.powergrid.controlcenter.config;

import br.com.powergrid.controlcenter.service.AlertaComunicacaoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "scada.monitoramento.comunicacao", name = "enabled", havingValue = "true")
public class AgendamentoComunicacao {
    private final AlertaComunicacaoService service;

    public AgendamentoComunicacao(AlertaComunicacaoService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${scada.monitoramento.comunicacao.fixed-delay:30000}")
    public void avaliar() {
        service.avaliar();
    }
}
