package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado pelo Q&A Service para o Notification Service quando uma pergunta
 * é aprovada e fica visível (ACTIVE). Notifica o VENDEDOR do anúncio.
 * Tópico: "qa.question.approved".
 */
public record QuestionApprovedNotification(
        UUID recipientId,
        Long auctionId,
        Long questionId,
        String questionText,
        Instant occurredAt,
        UUID correlationId
) {}
