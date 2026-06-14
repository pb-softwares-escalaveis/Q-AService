package br.com.leilao.dto.response;

import br.com.leilao.domain.enums.ContentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnswerResponse(
        Long id,
        Long questionId,
        UUID userId,
        String text,
        ContentStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}