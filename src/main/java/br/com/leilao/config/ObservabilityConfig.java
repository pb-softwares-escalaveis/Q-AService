package br.com.leilao.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita o processamento da anotação {@code @Timed} (Micrometer) para métricas
 * customizadas de tempo de execução (ex.: {@code OutboxProcessor}). As métricas são
 * expostas em /actuator/prometheus pelo micrometer-registry-prometheus e raspadas
 * pelo Prometheus do stack de observabilidade.
 */
@Configuration
public class ObservabilityConfig
{
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry)
    {
        return new TimedAspect(registry);
    }
}
