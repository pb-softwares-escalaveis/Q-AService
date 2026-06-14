package br.com.leilao.integration.kafka;

import br.com.leilao.domain.entity.OutboxEvent;
import br.com.leilao.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor
{

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findAllByOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                log.info("[OUTBOX] Enviando evento {} para o tópico {}", event.getId(), event.getTopic());

                // JSON para o Kafka
                kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()).get();

                outboxEventRepository.delete(event);

                log.info("[OUTBOX] Evento {} entregue e removido da tabela.", event.getId());

            } catch (Exception e) {
                log.error("[OUTBOX] Falha ao enviar evento {}: {}. Será tentado novamente no próximo ciclo.", event.getId(), e.getMessage());
                break;
            }
        }
    }
}