package br.com.leilao.dto.response;

import br.com.leilao.domain.enums.ContentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record QuestionResponse(
        Long id,
        Long auctionId,
        UUID userId,
        String text,
        ContentStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AnswerResponse answer
) {}