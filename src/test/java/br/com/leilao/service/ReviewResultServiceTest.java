package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.AnswerApprovedNotification;
import br.com.leilao.integration.kafka.events.AnswerRejectedNotification;
import br.com.leilao.integration.kafka.events.QuestionApprovedNotification;
import br.com.leilao.integration.kafka.events.QuestionRejectedNotification;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewResultServiceTest
{

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ReviewResultService reviewResultService;

    private Long auctionId;
    private Long questionId;
    private Long answerId;
    private UUID sellerId;
    private UUID buyerId;
    private UUID correlationId;

    private Question question;
    private Answer answer;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(reviewResultService, "questionApprovedTopic", "qa.question.approved");
        ReflectionTestUtils.setField(reviewResultService, "answerApprovedTopic", "qa.answer.approved");
        ReflectionTestUtils.setField(reviewResultService, "questionRejectedTopic", "qa.question.rejected");
        ReflectionTestUtils.setField(reviewResultService, "answerRejectedTopic", "qa.answer.rejected");

        auctionId = 1L;
        questionId = 1000L;
        answerId = 2000L;
        sellerId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        correlationId = UUID.randomUUID();

        question = Question.builder()
                .id(questionId)
                .auctionId(auctionId)
                .sellerId(sellerId)
                .authorId(buyerId)
                .text("O produto é original?")
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        answer = Answer.builder()
                .id(answerId)
                .question(question)
                .authorId(sellerId)
                .text("Sim, com nota fiscal.")
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();
    }


    @Test
    @DisplayName("Pergunta aprovada -> ACTIVE e notifica o VENDEDOR")
    void perguntaAprovadaNotificaVendedor()
    {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        reviewResultService.applyApproval(questionId, correlationId);

        assertEquals(ContentStatus.ACTIVE, question.getStatus());

        ArgumentCaptor<QuestionApprovedNotification> captor = ArgumentCaptor.forClass(QuestionApprovedNotification.class);
        verify(outboxEventPublisher).publish(eq("qa.question.approved"), captor.capture());
        assertEquals(sellerId, captor.getValue().recipientId());
        assertEquals(questionId, captor.getValue().questionId());
        assertEquals(correlationId, captor.getValue().correlationId());
        verify(answerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Resposta aprovada -> ACTIVE e notifica o COMPRADOR (autor da pergunta)")
    void respostaAprovadaNotificaComprador()
    {
        when(questionRepository.findById(answerId)).thenReturn(Optional.empty());
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));

        reviewResultService.applyApproval(answerId, correlationId);

        assertEquals(ContentStatus.ACTIVE, answer.getStatus());

        ArgumentCaptor<AnswerApprovedNotification> captor = ArgumentCaptor.forClass(AnswerApprovedNotification.class);
        verify(outboxEventPublisher).publish(eq("qa.answer.approved"), captor.capture());
        assertEquals(buyerId, captor.getValue().recipientId());
        assertEquals(questionId, captor.getValue().questionId());
        assertEquals(answerId, captor.getValue().answerId());
    }


    @Test
    @DisplayName("Pergunta rejeitada -> REJECTED e notifica o AUTOR da pergunta")
    void perguntaRejeitadaNotificaAutor() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        reviewResultService.applyRejection(questionId, "Conteúdo ofensivo", correlationId);

        assertEquals(ContentStatus.REJECTED, question.getStatus());
        assertEquals("Conteúdo ofensivo", question.getRejectionReason());

        ArgumentCaptor<QuestionRejectedNotification> captor = ArgumentCaptor.forClass(QuestionRejectedNotification.class);
        verify(outboxEventPublisher).publish(eq("qa.question.rejected"), captor.capture());
        assertEquals(buyerId, captor.getValue().recipientId());
        assertEquals("Conteúdo ofensivo", captor.getValue().reason());
    }

    @Test
    @DisplayName("Resposta rejeitada -> REJECTED e notifica o AUTOR da resposta (vendedor)")
    void respostaRejeitadaNotificaAutor() {
        when(questionRepository.findById(answerId)).thenReturn(Optional.empty());
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));

        reviewResultService.applyRejection(answerId, "Spam", correlationId);

        assertEquals(ContentStatus.REJECTED, answer.getStatus());
        assertEquals("Spam", answer.getRejectionReason());

        ArgumentCaptor<AnswerRejectedNotification> captor = ArgumentCaptor.forClass(AnswerRejectedNotification.class);
        verify(outboxEventPublisher).publish(eq("qa.answer.rejected"), captor.capture());
        assertEquals(sellerId, captor.getValue().recipientId());
        assertEquals(answerId, captor.getValue().answerId());
        assertEquals("Spam", captor.getValue().reason());
    }

    // Idempotência — guard "só processa se PENDING_ANALYSIS" (Item 4)

    @Test
    @DisplayName("Pergunta já ACTIVE -> ignora aprovação (redelivery); nada é salvo nem publicado")
    void perguntaJaActiveIgnoraAprovacao()
    {
        question.setStatus(ContentStatus.ACTIVE);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        reviewResultService.applyApproval(questionId, correlationId);

        assertEquals(ContentStatus.ACTIVE, question.getStatus());
        verify(questionRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), any());
        verify(answerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Pergunta DELETED -> ignora aprovação; não ressuscita pra ACTIVE")
    void perguntaDeletedIgnoraAprovacao()
    {
        question.setStatus(ContentStatus.DELETED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        reviewResultService.applyApproval(questionId, correlationId);

        assertEquals(ContentStatus.DELETED, question.getStatus());
        verify(questionRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Resposta já ACTIVE -> ignora aprovação (redelivery); nada é salvo nem publicado")
    void respostaJaActiveIgnoraAprovacao() {
        answer.setStatus(ContentStatus.ACTIVE);
        when(questionRepository.findById(answerId)).thenReturn(Optional.empty());
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));

        reviewResultService.applyApproval(answerId, correlationId);

        assertEquals(ContentStatus.ACTIVE, answer.getStatus());
        verify(answerRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Pergunta já REJECTED -> ignora rejeição (redelivery); reason original preservado")
    void perguntaJaRejectedIgnoraRejeicao() {
        question.setStatus(ContentStatus.REJECTED);
        question.setRejectionReason("Motivo original");
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        reviewResultService.applyRejection(questionId, "Motivo novo (não deve aplicar)", correlationId);

        assertEquals(ContentStatus.REJECTED, question.getStatus());
        assertEquals("Motivo original", question.getRejectionReason());
        verify(questionRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), any());
    }
}
