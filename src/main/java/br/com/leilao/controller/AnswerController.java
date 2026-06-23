package br.com.leilao.controller;

import br.com.leilao.aop.RateLimited;
import br.com.leilao.dto.request.CreateAnswerRequest;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.service.AnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class AnswerController
{
    private final AnswerService answerService;

    @RateLimited("create-answer")
    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<AnswerResponse> createAnswer(
            @PathVariable Long questionId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Allowed") boolean allowed,
            @Valid @RequestBody CreateAnswerRequest request) {
        AnswerResponse response = answerService.createAnswer(questionId, userId, allowed, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/questions/{questionId}/answers/{answerId}")
    public ResponseEntity<Void> deleteAnswer(
            @PathVariable Long questionId,
            @PathVariable Long answerId,
            @RequestHeader("X-User-Id") UUID userId) {
        answerService.deleteAnswer(questionId, answerId, userId);
        return ResponseEntity.noContent().build();
    }

}