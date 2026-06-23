package br.com.leilao.integration.kafka.events;

import br.com.leilao.domain.entity.Answer;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado pelo Q&A Service para o Notification Service quando uma resposta
 * é aprovada e fica visível (ACTIVE). Notifica o COMPRADOR que fez a pergunta.
 * Tópico: "qa.answer.approved".
 */
public record AnswerApprovedNotification(
        UUID recipientId,
        Long auctionId,
        Long questionId,
        Long answerId,
        String questionText,
        String answerText,
        Instant occurredAt,
        UUID correlationId
) {
    public static AnswerApprovedNotification forBuyerOf(Answer answer, UUID correlationId) {
        var question = answer.getQuestion();
        return new AnswerApprovedNotification(
                question.getAuthorId(),
                question.getAuctionId(),
                question.getId(),
                answer.getId(),
                question.getText(),
                answer.getText(),
                Instant.now(),
                correlationId
        );
    }
}
