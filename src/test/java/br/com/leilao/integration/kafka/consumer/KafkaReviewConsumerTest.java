package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import br.com.leilao.service.ReviewResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


@ExtendWith(MockitoExtension.class)
class KafkaReviewConsumerTest
{
    @Mock
    private ReviewResultService reviewResultService;

    @InjectMocks
    private KafkaReviewConsumer consumer;

    @Test
    @DisplayName("consumeApproved -> delega para applyApproval com messageId e correlationId")
    void aprovacaoDelegaParaService() {
        Long messageId = 1234L;
        UUID correlationId = UUID.randomUUID();
        MessageReviewApproved event = new MessageReviewApproved(
                1L, UUID.randomUUID(), messageId, Instant.now(), correlationId);

        consumer.consumeApproved(event);

        verify(reviewResultService).applyApproval(messageId, correlationId);
        verifyNoMoreInteractions(reviewResultService);
    }

    @Test
    @DisplayName("consumeRejected -> delega para applyRejection com messageId, reason e correlationId")
    void rejeicaoDelegaParaService() {
        Long messageId = 5678L;
        UUID correlationId = UUID.randomUUID();
        String reason = "Conteúdo ofensivo";
        MessageReviewRejected event = new MessageReviewRejected(
                1L, UUID.randomUUID(), messageId, reason, Instant.now(), correlationId);

        consumer.consumeRejected(event);

        verify(reviewResultService).applyRejection(messageId, reason, correlationId);
        verifyNoMoreInteractions(reviewResultService);
    }
}
