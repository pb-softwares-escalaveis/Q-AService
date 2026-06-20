package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import br.com.leilao.service.ReviewResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaReviewConsumer
{
    private final ReviewResultService reviewResultService;

    @KafkaListener(topics = "${app.kafka.topics.qa-review-approved}", groupId = "qa-group")
    public void consumeApproved(MessageReviewApproved event)
    {
        log.info("[KAFKA CONSUMER] Conteúdo aprovado recebido: messageId={}", event.messageId());
        reviewResultService.applyApproval(event.messageId(), event.correlationId());
    }

    @KafkaListener(topics = "${app.kafka.topics.qa-review-rejected}", groupId = "qa-group")
    public void consumeRejected(MessageReviewRejected event)
    {
        log.info("[KAFKA CONSUMER] Conteúdo rejeitado recebido: messageId={} | reason={}", event.messageId(), event.reason());
        reviewResultService.applyRejection(event.messageId(), event.reason(), event.correlationId());
    }
}
