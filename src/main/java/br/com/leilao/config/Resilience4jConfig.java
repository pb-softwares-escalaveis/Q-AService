package br.com.leilao.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class Resilience4jConfig {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer()
    {
        return new RegistryEventConsumer<>()
        {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker cb = entryAddedEvent.getAddedEntry();
                cb.getEventPublisher()
                        .onError(event -> log.error("[RESILIENCE-CB] Falha detectada no CircuitBreaker '{}': {}", cb.getName(), event.getThrowable().getMessage()))
                        .onStateTransition(event -> log.warn("[RESILIENCE-CB] CircuitBreaker '{}' mudou de estado de {} para {}",
                                cb.getName(), event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {}

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {}
        };
    }

    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                Retry retry = entryAddedEvent.getAddedEntry();
                retry.getEventPublisher()
                        .onRetry(event -> log.warn("[RESILIENCE-RETRY] Tentativa {} de repetição para '{}' após falha: {}",
                                event.getNumberOfRetryAttempts(), retry.getName(), event.getLastThrowable().getMessage()))
                        .onError(event -> log.error("[RESILIENCE-RETRY] Falha final no Retry '{}' após {} tentativas.",
                                retry.getName(), event.getNumberOfRetryAttempts()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {}

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {}
        };
    }
}
