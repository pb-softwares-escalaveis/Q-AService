package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record MessageReviewRejected(
        Long auctionId,
        UUID sellerId,
        UUID messageId,
        String sellerName,
        String sellerEmail,
        String reason,
        Instant ocurredAt,
        UUID correlationId
) {}