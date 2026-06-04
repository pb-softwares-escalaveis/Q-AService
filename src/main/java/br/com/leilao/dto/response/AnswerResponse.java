package br.com.leilao.dto.response;

import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.domain.enums.RejectionReason;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID questionId,
        UUID userId,
        String text,
        ContentStatus status,
        RejectionReason rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}