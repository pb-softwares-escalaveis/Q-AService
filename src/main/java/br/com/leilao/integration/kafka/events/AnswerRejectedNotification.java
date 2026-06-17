package br.com.leilao.integration.kafka.events;

import br.com.leilao.domain.entity.Answer;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado pelo Q&A Service para o Notification Service quando uma resposta
 * é rejeitada pela moderação. Notifica o AUTOR da resposta (vendedor).
 * Tópico: "qa.answer.rejected".
 * {@code reason} é o texto livre devolvido pela IA de moderação.
 */
public record AnswerRejectedNotification(
        UUID recipientId,
        Long auctionId,
        Long questionId,
        Long answerId,
        String answerText,
        String reason,
        Instant occurredAt,
        UUID correlationId
) {
    public static AnswerRejectedNotification forAuthorOf(Answer answer, String reason, UUID correlationId) {
        var question = answer.getQuestion();
        return new AnswerRejectedNotification(
                answer.getAuthorId(),
                question.getAuctionId(),
                question.getId(),
                answer.getId(),
                answer.getText(),
                reason,
                Instant.now(),
                correlationId
        );
    }
}
