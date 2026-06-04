package br.com.leilao.controller;

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

    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<AnswerResponse> createAnswer(
            @PathVariable UUID questionId,
            @RequestParam UUID userId,
            @Valid @RequestBody CreateAnswerRequest request) {
        AnswerResponse response = answerService.createAnswer(questionId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}