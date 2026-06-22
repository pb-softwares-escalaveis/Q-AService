package br.com.leilao.integration.kafka;

import br.com.leilao.domain.entity.OutboxEvent;
import br.com.leilao.domain.enums.OutboxStatus;
import br.com.leilao.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest
{

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    private OutboxEvent event;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(outboxProcessor, "maxAttempts", 3);

        event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("qa.review.created-pending")
                .aggregateId("42")
                .payload("{\"foo\":\"bar\"}")
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    @Test
    @DisplayName("Sucesso: publica no Kafka, deleta o evento e usa aggregateId como key")
    void sucessoDeletaEvento()
    {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("qa.review.created-pending"), eq("42"), eq("{\"foo\":\"bar\"}")))
                .thenReturn(completedFuture("qa.review.created-pending", 0, 0));

        outboxProcessor.processOutbox();

        verify(kafkaTemplate).send("qa.review.created-pending", "42", "{\"foo\":\"bar\"}");
        verify(outboxEventRepository).delete(event);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Primeira falha: attemptCount=1, lastAttemptAt setado, status segue PENDING e evento é salvo (não deletado)")
    void primeiraFalhaIncrementaContador()
    {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture(new RuntimeException("broker fora")));

        outboxProcessor.processOutbox();

        assertEquals(1, event.getAttemptCount());
        assertNotNull(event.getLastAttemptAt());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(outboxEventRepository).save(event);
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    @DisplayName("N-ésima falha (atinge maxAttempts): status vira FAILED e é salvo")
    void atingeMaxAttemptsViraFailed()
    {
        event.setAttemptCount(2);
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture(new RuntimeException("payload invalido")));

        outboxProcessor.processOutbox();

        assertEquals(3, event.getAttemptCount());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        verify(outboxEventRepository).save(event);
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Filtragem por status: o processor consulta apenas PENDING")
    void consultaApenasPending()
    {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        outboxProcessor.processOutbox();

        ArgumentCaptor<OutboxStatus> statusCaptor = ArgumentCaptor.forClass(OutboxStatus.class);
        verify(outboxEventRepository).findByStatusOrderByCreatedAtAsc(statusCaptor.capture(), any(Pageable.class));
        assertEquals(OutboxStatus.PENDING, statusCaptor.getValue());
    }

    @Test
    @DisplayName("Kafka recebe aggregateId como key — garantia de ordem por leilão")
    void kafkaRecebeAggregateIdComoKey()
    {
        event.setAggregateId("999");
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(completedFuture("qa.review.created-pending", 0, 0));

        outboxProcessor.processOutbox();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("qa.review.created-pending"), keyCaptor.capture(), anyString());
        assertEquals("999", keyCaptor.getValue());
    }

    private static CompletableFuture<SendResult<String, String>> completedFuture(String topic, int partition, long offset)
    {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, partition), offset, 0, 0L, 0, 0);
        SendResult<String, String> result = new SendResult<>(null, metadata);
        return CompletableFuture.completedFuture(result);
    }

    private static CompletableFuture<SendResult<String, String>> failedFuture(Throwable cause)
    {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }
}
