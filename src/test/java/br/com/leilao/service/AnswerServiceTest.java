package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateAnswerRequest;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.OutboxEventRepository;
import br.com.leilao.service.mapper.AnswerMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest
{

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private QuestionService questionService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AnswerMapper answerMapper;

    @InjectMocks
    private AnswerService answerService;

    private Long auctionId;
    private UUID questionId;
    private UUID answerId;
    private UUID sellerId;
    private UUID buyerId;

    private Question question;
    private CreateAnswerRequest createRequest;
    private Answer savedAnswer;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(answerService, "topic", "qa.question.created");

        auctionId = 1L;
        questionId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        buyerId = UUID.randomUUID();

        createRequest = new CreateAnswerRequest("Esta é uma resposta de teste.");

        question = Question.builder()
                .id(questionId)
                .auctionId(auctionId)
                .sellerId(sellerId)
                .userId(buyerId)
                .text("Pergunta?")
                .status(ContentStatus.ACTIVE)
                .build();

        savedAnswer = Answer.builder()
                .id(answerId)
                .question(question)
                .userId(sellerId)
                .text(createRequest.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve criar uma Answer com sucesso e gravar no Outbox")
    void deveCriarAnswerComSucesso() throws JsonProcessingException
    {
        // Arrange
        AnswerResponse expectedResponse = new AnswerResponse(
                answerId, questionId, sellerId, createRequest.text(),
                ContentStatus.PENDING_ANALYSIS, null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(questionService.getQuestionById(questionId)).thenReturn(question);
        when(answerRepository.existsByQuestion_Id(questionId)).thenReturn(false);
        when(answerRepository.save(any(Answer.class))).thenReturn(savedAnswer);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"payload\": \"teste\"}");
        when(answerMapper.toResponse(any(Answer.class))).thenReturn(expectedResponse);

        // Act
        AnswerResponse response = answerService.createAnswer(questionId, sellerId, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(ContentStatus.PENDING_ANALYSIS, response.status());

        verify(answerRepository).save(any(Answer.class));
        verify(outboxEventRepository).save(any()); // Verifica salvamento no Outbox
    }

    @Test
    @DisplayName("Deve lançar ForbiddenOperationException ao tentar criar Answer não sendo o vendedor")
    void deveCriarAnswerRetornarForbidden()
    {
        UUID fakeUserId = UUID.randomUUID();
        when(questionService.getQuestionById(questionId)).thenReturn(question);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> answerService.createAnswer(questionId, fakeUserId, createRequest)
        );

        assertEquals("Apenas o vendedor do anúncio pode responder a pergunta.", exception.getMessage());
        verifyNoInteractions(answerRepository, outboxEventRepository, answerMapper);
    }
}