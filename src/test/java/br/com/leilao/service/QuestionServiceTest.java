package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.integration.feign.AuctionClient;
import br.com.leilao.integration.feign.dto.AuctionResponse;
import br.com.leilao.integration.kafka.KafkaProducerService;
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
    private AuctionClient auctionClient;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionService questionService;

    private Long auctionId;
    private UUID userId;
    private UUID sellerId;
    private UUID questionId;
    private CreateQuestionRequest createRequest;
    private Question savedQuestion;
    private AuctionResponse auctionResponse;
    private QuestionResponse questionResponse;

    @BeforeEach
    void setUp()
    {
        auctionId = 1L;
        userId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        questionId = UUID.randomUUID();

        createRequest = new CreateQuestionRequest("Esta é uma pergunta de teste?");
        auctionResponse = new AuctionResponse(auctionId, sellerId);

        savedQuestion = Question.builder()
                .id(questionId)
                .auctionId(auctionId)
                .sellerId(sellerId)
                .userId(userId)
                .text(createRequest.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        questionResponse = new QuestionResponse(
                questionId,
                auctionId,
                userId,
                createRequest.text(),
                ContentStatus.PENDING_ANALYSIS,
                null,
                savedQuestion.getCreatedAt(),
                savedQuestion.getUpdatedAt(),
                null
        );
    }

    @Test
    @DisplayName("Deve criar uma Question com sucesso e retornar o status PENDING_ANALYSIS")
    void deveCriarQuestionComSucesso()
    {
        // Arrange
        when(auctionClient.getAdById(auctionId)).thenReturn(auctionResponse);
        when(questionRepository.save(any(Question.class))).thenReturn(savedQuestion);
        when(questionMapper.toResponse(any(Question.class))).thenReturn(questionResponse);

        // Act
        QuestionResponse response = questionService.createQuestion(auctionId, userId, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(questionId, response.id());
        assertEquals(auctionId, response.auctionId());
        assertEquals(userId, response.userId());
        assertEquals(ContentStatus.PENDING_ANALYSIS, response.status());

        verify(auctionClient, times(1)).getAdById(auctionId);
        verify(questionRepository, times(1)).save(any(Question.class));
        verify(kafkaProducerService, times(1)).sendForReview(any());
        verifyNoInteractions(notificationPublisher);
        verify(questionMapper).toResponse(any(Question.class));
    }

}