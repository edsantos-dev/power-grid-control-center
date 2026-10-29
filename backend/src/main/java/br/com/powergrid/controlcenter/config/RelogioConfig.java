package br.com.powergrid.controlcenter.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RelogioConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
