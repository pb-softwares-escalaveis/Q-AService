package br.com.leilao.integration.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record MessageReviewRejected(
        Long auctionId,
        UUID sellerId,
        Long messageId,
        String reason,
        Instant occurredAt,
        UUID correlationId
) {}