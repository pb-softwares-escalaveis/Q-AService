package br.com.leilao.integration.kafka;

import br.com.leilao.domain.entity.OutboxEvent;
import br.com.leilao.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor
{

    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 10000)
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : events) {
            try {
                log.info("[OUTBOX] Enviando evento {} para o tópico {}", event.getId(), event.getTopic());

                kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()).get();

                outboxEventRepository.delete(event);

                log.info("[OUTBOX] Evento {} entregue e removido da tabela.", event.getId());

            } catch (Exception e) {
                log.error("[OUTBOX] Falha ao enviar evento {}: {}. Será tentado novamente no próximo ciclo.", event.getId(), e.getMessage());
            }
        }
    }
}
