package br.com.leilao.controller;

import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.request.UpdateQuestionRequest;
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

    private final QuestionService questionService;

    @PostMapping("/ads/{adId}/questions")
    public ResponseEntity<QuestionResponse> createQuestion(
            @PathVariable UUID adId,
            @RequestParam UUID userId,
            @Valid @RequestBody CreateQuestionRequest request) {
        QuestionResponse response = questionService.createQuestion(adId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable UUID questionId,
            @RequestParam UUID userId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        QuestionResponse response = questionService.updateQuestion(questionId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID questionId,
            @RequestParam UUID userId) {
        questionService.deleteQuestion(questionId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ads/{adId}/questions")
    public ResponseEntity<Page<QuestionResponse>> listQuestions(
            @PathVariable UUID adId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<QuestionResponse> response = questionService.listActiveQuestions(adId, pageable);
        return ResponseEntity.ok(response);
    }
}