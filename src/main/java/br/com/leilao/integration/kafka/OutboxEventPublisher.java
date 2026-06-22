package br.com.leilao.integration.kafka;

import br.com.leilao.domain.entity.OutboxEvent;
import br.com.leilao.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher
{

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publish(String topic, String aggregateId, Object event)
    {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .topic(topic)
                    .aggregateId(aggregateId)
                    .payload(payloadJson)
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar evento para o Outbox", e);
        }
    }
}
