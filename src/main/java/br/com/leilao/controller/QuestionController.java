package br.com.leilao.controller;

import br.com.leilao.aop.RateLimited;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QuestionController
{

    private static final String SORT_FIELD_CREATED_AT = "createdAt";

    private final QuestionService questionService;

    @RateLimited("create-question")
    @PostMapping("/auctions/{auctionId}/questions")
    public ResponseEntity<QuestionResponse> createQuestion(
            @PathVariable Long auctionId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Allowed") boolean allowed,
            @Valid @RequestBody CreateQuestionRequest request) {
        QuestionResponse response = questionService.createQuestion(auctionId, userId, allowed, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long questionId,
            @RequestHeader("X-User-Id") UUID userId) {
        questionService.deleteQuestion(questionId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auctions/{auctionId}/questions")
    public ResponseEntity<Page<QuestionResponse>> listQuestions(
            @PathVariable Long auctionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(SORT_FIELD_CREATED_AT).ascending());
        Page<QuestionResponse> response = questionService.listActiveQuestions(auctionId, pageable);
        return ResponseEntity.ok(response);
    }
}