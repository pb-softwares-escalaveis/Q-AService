package br.com.leilao.integration.kafka.events;

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
) {}
