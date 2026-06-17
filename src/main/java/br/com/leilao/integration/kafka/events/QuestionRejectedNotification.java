package br.com.leilao.integration.kafka.events;

import br.com.leilao.domain.entity.Question;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado pelo Q&A Service para o Notification Service quando uma pergunta
 * é rejeitada pela moderação. Notifica o AUTOR da pergunta (comprador).
 * Tópico: "qa.question.rejected".
 */
public record QuestionRejectedNotification(
        UUID recipientId,
        Long auctionId,
        Long questionId,
        String questionText,
        String reason,
        Instant occurredAt,
        UUID correlationId
) {
    public static QuestionRejectedNotification forAuthorOf(Question question, String reason, UUID correlationId) {
        return new QuestionRejectedNotification(
                question.getAuthorId(),
                question.getAuctionId(),
                question.getId(),
                question.getText(),
                reason,
                Instant.now(),
                correlationId
        );
    }
}
