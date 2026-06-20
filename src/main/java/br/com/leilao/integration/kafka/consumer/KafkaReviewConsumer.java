package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import br.com.leilao.service.ReviewResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaReviewConsumer
{
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ReviewResultService reviewResultService;

    @KafkaListener(topics = "${app.kafka.topics.qa-review-approved}", groupId = "qa-group")
    public void consumeApproved(MessageReviewApproved event)
    {
        withCorrelationId(event.correlationId(), () -> {
            log.info("[KAFKA CONSUMER] Conteúdo aprovado recebido: messageId={}", event.messageId());
            reviewResultService.applyApproval(event.messageId(), event.correlationId());
        });
    }

    @KafkaListener(topics = "${app.kafka.topics.qa-review-rejected}", groupId = "qa-group")
    public void consumeRejected(MessageReviewRejected event)
    {
        withCorrelationId(event.correlationId(), () -> {
            log.info("[KAFKA CONSUMER] Conteúdo rejeitado recebido: messageId={} | reason={}", event.messageId(), event.reason());
            reviewResultService.applyRejection(event.messageId(), event.reason(), event.correlationId());
        });
    }

    private void withCorrelationId(UUID correlationId, Runnable action)
    {
        String value = correlationId != null ? correlationId.toString() : "none";
        MDC.put(MDC_CORRELATION_ID, value);
        try {
            action.run();
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
