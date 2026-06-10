package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record MessageReviewApproved(
        Long auctionId,
        UUID sellerId,
        UUID messageId,
        String sellerName,
        String sellerEmail,
        String message,
        Instant ocurredAt,
        UUID correlationId
) {}