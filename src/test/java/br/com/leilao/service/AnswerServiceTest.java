package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateAnswerRequest;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.integration.notification.NotificationPublisher;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.service.mapper.AnswerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private QuestionService questionService;

    @Mock
    private ContentAnalysisMockService contentAnalysisService;

    @Mock
    private NotificationPublisher notificationPublisher;

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
                .status(ContentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }


    @Test
    @DisplayName("Deve criar uma Answer com sucesso quando respondida pelo vendedor")
    void deveCriarAnswerComSucesso()
    {
        // Arrange
        AnswerResponse expectedResponse = new AnswerResponse(
                answerId,
                questionId,
                sellerId,
                createRequest.text(),
                ContentStatus.ACTIVE,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(questionService.getQuestionById(questionId)).thenReturn(question);
        when(answerRepository.existsByQuestion_Id(questionId)).thenReturn(false);
        when(answerRepository.save(any(Answer.class))).thenReturn(savedAnswer);
        when(answerMapper.toResponse(any(Answer.class))).thenReturn(expectedResponse);



        // Act
        AnswerResponse response = answerService.createAnswer(questionId, sellerId, createRequest);



        // Assert
        assertNotNull(response);
        assertEquals(answerId, response.id());
        assertEquals(sellerId, response.userId());
        assertEquals(questionId, response.questionId());
        assertEquals(ContentStatus.ACTIVE, response.status());

        verify(questionService).getQuestionById(questionId);
        verify(answerRepository).existsByQuestion_Id(questionId);
        verify(answerRepository).save(any(Answer.class));
        verify(contentAnalysisService).analyze(any(Answer.class));
        verify(notificationPublisher).notifyBuyerNewAnswer(buyerId, questionId, auctionId);
        verify(answerMapper).toResponse(any(Answer.class));           // ← verificar chamada
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

        verify(questionService).getQuestionById(questionId);
        verifyNoInteractions(answerRepository, contentAnalysisService,
                notificationPublisher, answerMapper);
    }
}
