package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.AnswerApprovedNotification;
import br.com.leilao.integration.kafka.events.AnswerRejectedNotification;
import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaReviewConsumerTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private KafkaReviewConsumer consumer;

    private Long auctionId;
    private Long questionId;
    private Long answerId;
    private UUID sellerId;
    private UUID buyerId;
    private UUID correlationId;

    private Question question;
    private Answer answer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "questionApprovedTopic", "qa.question.approved");
        ReflectionTestUtils.setField(consumer, "answerApprovedTopic", "qa.answer.approved");
        ReflectionTestUtils.setField(consumer, "questionRejectedTopic", "qa.question.rejected");
        ReflectionTestUtils.setField(consumer, "answerRejectedTopic", "qa.answer.rejected");

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
                .userId(buyerId)
                .text("O produto é original?")
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        answer = Answer.builder()
                .id(answerId)
                .question(question)
                .userId(sellerId)
                .text("Sim, com nota fiscal.")
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();
    }

    @Test
    @DisplayName("Pergunta aprovada -> ACTIVE e notifica o VENDEDOR")
    void perguntaAprovadaNotificaVendedor() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        MessageReviewApproved event = new MessageReviewApproved(auctionId, sellerId, questionId, Instant.now(), correlationId);

        consumer.consumeApproved(event);

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
    void respostaAprovadaNotificaComprador() {
        when(questionRepository.findById(answerId)).thenReturn(Optional.empty());
        when(answerRepository.findById(answerId)).thenReturn(Optional.of(answer));
        MessageReviewApproved event = new MessageReviewApproved(auctionId, sellerId, answerId, Instant.now(), correlationId);

        consumer.consumeApproved(event);

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
        MessageReviewRejected event = new MessageReviewRejected(auctionId, sellerId, questionId, "Conteúdo ofensivo", Instant.now(), correlationId);

        consumer.consumeRejected(event);

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
        MessageReviewRejected event = new MessageReviewRejected(auctionId, sellerId, answerId, "Spam", Instant.now(), correlationId);

        consumer.consumeRejected(event);

        assertEquals(ContentStatus.REJECTED, answer.getStatus());
        assertEquals("Spam", answer.getRejectionReason());

        ArgumentCaptor<AnswerRejectedNotification> captor = ArgumentCaptor.forClass(AnswerRejectedNotification.class);
        verify(outboxEventPublisher).publish(eq("qa.answer.rejected"), captor.capture());
        assertEquals(sellerId, captor.getValue().recipientId());
        assertEquals(answerId, captor.getValue().answerId());
        assertEquals("Spam", captor.getValue().reason());
    }
}
