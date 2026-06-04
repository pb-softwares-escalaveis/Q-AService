package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.integration.feign.AdClient;
import br.com.leilao.integration.feign.dto.AdResponse;
import br.com.leilao.integration.notification.NotificationPublisher;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.QuestionMapper;
import org.junit.jupiter.api.AfterEach;
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
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AdClient adClient;

    @Mock
    private ContentAnalysisMockService contentAnalysisService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionService questionService;

    private UUID adId;
    private UUID userId;
    private UUID sellerId;
    private UUID questionId;
    private CreateQuestionRequest createRequest;
    private Question savedQuestion;
    private AdResponse adResponse;
    private QuestionResponse questionResponse;

    @BeforeEach
    void setUp()
    {
        adId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        questionId = UUID.randomUUID();

        createRequest = new CreateQuestionRequest("Esta é uma pergunta de teste?");
        adResponse = new AdResponse(adId, sellerId);

        savedQuestion = Question.builder()
                .id(questionId)
                .adId(adId)
                .sellerId(sellerId)
                .userId(userId)
                .text(createRequest.text())
                .status(ContentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        questionResponse = new QuestionResponse(
                questionId,
                adId,
                userId,
                createRequest.text(),
                ContentStatus.ACTIVE,
                null,
                savedQuestion.getCreatedAt(),
                savedQuestion.getUpdatedAt(),
                null
        );
    }

    @Test
    @DisplayName("Deve criar uma Question com sucesso e retornar o status ACTIVE")
    void deveCriarQuestionComSucesso()
    {
        // Arrange
        when(adClient.getAdById(adId)).thenReturn(adResponse);
        when(questionRepository.save(any(Question.class))).thenReturn(savedQuestion);
        when(questionMapper.toResponse(any(Question.class))).thenReturn(questionResponse);

        // Act
        QuestionResponse response = questionService.createQuestion(adId, userId, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(questionId, response.id());
        assertEquals(adId, response.adId());
        assertEquals(userId, response.userId());
        assertEquals(ContentStatus.ACTIVE, response.status());

        verify(adClient, times(1)).getAdById(adId);
        verify(questionRepository, times(1)).save(any(Question.class));
        verify(contentAnalysisService, times(1)).analyze(any(Question.class));
        verify(notificationPublisher, times(1)).notifySellerNewQuestion(sellerId, questionId, adId);
        verify(questionMapper).toResponse(any(Question.class));
    }

}