package br.com.leilao.stub;

import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Stub local do review-service. Ativo apenas com o profile "review-stub".
 *
 * Consome {@code qa.review.created-pending} e devolve aprovação ou rejeição
 * mantendo o contrato real ({@code MessageReviewApproved}/{@code MessageReviewRejected}).
 * Rejeita se o texto contiver alguma keyword bloqueada — útil para exercitar
 * os dois caminhos do {@code KafkaReviewConsumer} num único JVM, sem depender
 * do review-service externo.
 *
 * Como ativar:
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,review-stub
 */
@Slf4j
@Component
@Profile("review-stub")
@RequiredArgsConstructor
public class ReviewServiceStub
{

    private static final Set<String> BLOCKED_KEYWORDS = Set.of("spam", "ofensa", "fraude");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.qa-review-approved}")
    private String approvedTopic;

    @Value("${app.kafka.topics.qa-review-rejected}")
    private String rejectedTopic;

    @KafkaListener(topics = "${app.kafka.topics.qa-review-created-pending}", groupId = "review-stub-group")
    public void review(MessageCreatedPendingReview event) throws JsonProcessingException {
        String text = event.message() == null ? "" : event.message().toLowerCase();
        String blocked = BLOCKED_KEYWORDS.stream().filter(text::contains).findFirst().orElse(null);

        if (blocked != null) {
            MessageReviewRejected rejected = new MessageReviewRejected(
                    event.auctionId(),
                    event.sellerId(),
                    event.messageId(),
                    "Stub: palavra bloqueada detectada (" + blocked + ")",
                    Instant.now(),
                    event.correlationId()
            );
            kafkaTemplate.send(rejectedTopic, event.messageId().toString(), objectMapper.writeValueAsString(rejected));
            log.info("[REVIEW-STUB] messageId={} REJEITADO (keyword='{}')", event.messageId(), blocked);
            return;
        }

        MessageReviewApproved approved = new MessageReviewApproved(
                event.auctionId(),
                event.sellerId(),
                event.messageId(),
                Instant.now(),
                event.correlationId()
        );
        kafkaTemplate.send(approvedTopic, event.messageId().toString(), objectMapper.writeValueAsString(approved));
        log.info("[REVIEW-STUB] messageId={} APROVADO", event.messageId());
    }
}
