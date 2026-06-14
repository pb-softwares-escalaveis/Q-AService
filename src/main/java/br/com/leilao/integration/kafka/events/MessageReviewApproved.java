package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record MessageReviewApproved(
        Long auctionId,
        UUID sellerId,
        Long messageId,
        Instant ocurredAt,
        UUID correlationId
) {}
