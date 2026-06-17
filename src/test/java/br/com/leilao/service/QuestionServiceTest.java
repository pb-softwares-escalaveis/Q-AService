package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.integration.feign.AuctionClient;
import br.com.leilao.integration.feign.UserClient;
import br.com.leilao.integration.feign.dto.AuctionResponse;
import br.com.leilao.integration.feign.dto.UserResponse;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.QuestionMapper;
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
class QuestionServiceTest
{

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AuctionClient auctionClient;

    @Mock
    private UserClient userClient;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private QuestionMapper questionMapper;

    @InjectMocks
    private QuestionService questionService;

    private Long auctionId;
    private UUID userId;
    private UUID sellerId;
    private Long questionId;
    private CreateQuestionRequest createRequest;
    private Question savedQuestion;
    private AuctionResponse auctionResponse;
    private QuestionResponse questionResponse;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(questionService, "topic", "qa.review.created-pending");

        auctionId = 1L;
        userId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        questionId = 100L;

        createRequest = new CreateQuestionRequest("Esta é uma pergunta de teste?");
        auctionResponse = new AuctionResponse(auctionId, sellerId);

        savedQuestion = Question.builder()
                .id(questionId)
                .auctionId(auctionId)
                .sellerId(sellerId)
                .authorId(userId)
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
    @DisplayName("Deve criar uma Question com sucesso e publicar no Outbox")
    void deveCriarQuestionComSucesso()
    {
        // Arrange
        when(auctionClient.getAuctionById(auctionId)).thenReturn(auctionResponse);
        when(userClient.getUserById(sellerId)).thenReturn(new UserResponse("Vendedor", "vendedor@test.com"));
        when(questionRepository.save(any(Question.class))).thenReturn(savedQuestion);
        when(questionMapper.toResponse(any(Question.class))).thenReturn(questionResponse);

        // Act
        QuestionResponse response = questionService.createQuestion(auctionId, userId, true, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(questionId, response.id());
        assertEquals(ContentStatus.PENDING_ANALYSIS, response.status());

        verify(questionRepository).save(any(Question.class));
        verify(userClient).getUserById(sellerId);
        verify(outboxEventPublisher).publish(eq("qa.review.created-pending"), any());
        verify(questionMapper).toResponse(any(Question.class));
    }

    @Test
    @DisplayName("Deve lançar ForbiddenOperationException ao criar Question com usuário não autorizado")
    void deveBloquearQuestionUsuarioNaoAutorizado()
    {
        assertThrows(
                br.com.leilao.exception.ForbiddenOperationException.class,
                () -> questionService.createQuestion(auctionId, userId, false, createRequest)
        );

        verifyNoInteractions(auctionClient, userClient, questionRepository, outboxEventPublisher, questionMapper);
    }
}
