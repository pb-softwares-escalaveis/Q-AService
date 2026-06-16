package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateAnswerRequest;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.integration.feign.UserClient;
import br.com.leilao.integration.feign.dto.UserResponse;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.service.mapper.AnswerMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest
{

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private QuestionService questionService;

    @Mock
    private UserClient userClient;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private AnswerMapper answerMapper;

    @InjectMocks
    private AnswerService answerService;

    private Long auctionId;
    private Long questionId;
    private Long answerId;
    private UUID sellerId;
    private UUID buyerId;

    private Question question;
    private CreateAnswerRequest createRequest;
    private Answer savedAnswer;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(answerService, "topic", "qa.review.created-pending");

        auctionId = 1L;
        questionId = 100L;
        answerId = 200L;
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
    @DisplayName("Deve criar uma Answer com sucesso e publicar no Outbox")
    void deveCriarAnswerComSucesso()
    {
        // Arrange
        AnswerResponse expectedResponse = new AnswerResponse(
                answerId, questionId, sellerId, createRequest.text(),
                ContentStatus.PENDING_ANALYSIS, null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(questionService.getQuestionById(questionId)).thenReturn(question);
        when(answerRepository.existsByQuestion_Id(questionId)).thenReturn(false);
        when(userClient.getUserById(sellerId)).thenReturn(new UserResponse("Vendedor", "vendedor@test.com"));
        when(answerRepository.save(any(Answer.class))).thenReturn(savedAnswer);
        when(answerMapper.toResponse(any(Answer.class))).thenReturn(expectedResponse);

        // Act
        AnswerResponse response = answerService.createAnswer(questionId, sellerId, true, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(ContentStatus.PENDING_ANALYSIS, response.status());

        verify(answerRepository).save(any(Answer.class));
        verify(userClient).getUserById(sellerId);
        verify(outboxEventPublisher).publish(eq("qa.review.created-pending"), any());
    }

    @Test
    @DisplayName("Deve lançar ForbiddenOperationException ao tentar criar Answer não sendo o vendedor")
    void deveCriarAnswerRetornarForbidden()
    {
        UUID fakeUserId = UUID.randomUUID();
        when(questionService.getQuestionById(questionId)).thenReturn(question);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> answerService.createAnswer(questionId, fakeUserId, true, createRequest)
        );

        assertEquals("Apenas o vendedor do anúncio pode responder a pergunta.", exception.getMessage());
        verifyNoInteractions(answerRepository, userClient, outboxEventPublisher, answerMapper);
    }

    @Test
    @DisplayName("Deve lançar ForbiddenOperationException ao criar Answer com usuário não autorizado")
    void deveBloquearAnswerUsuarioNaoAutorizado()
    {
        assertThrows(
                ForbiddenOperationException.class,
                () -> answerService.createAnswer(questionId, sellerId, false, createRequest)
        );

        verifyNoInteractions(questionService, answerRepository, userClient, outboxEventPublisher, answerMapper);
    }
}
