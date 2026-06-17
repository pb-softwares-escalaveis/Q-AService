package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedPendingReview(
        Long auctionId,
        UUID sellerId,
        Long messageId,
        String sellerName,
        String sellerEmail,
        String message,
        Instant occurredAt,
        UUID correlationId
) {}