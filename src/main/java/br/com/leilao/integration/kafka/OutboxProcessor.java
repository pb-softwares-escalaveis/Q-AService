package br.com.leilao.integration.kafka;

import br.com.leilao.domain.entity.OutboxEvent;
import br.com.leilao.domain.enums.OutboxStatus;
import br.com.leilao.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor
{

    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.max-attempts:10}")
    private int maxAttempts;

    @Scheduled(fixedDelay = 10000)
    public void processOutbox()
    {
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : events) {
            try {
                log.info("[OUTBOX] Enviando evento {} para o tópico {} (aggregateId={})",
                        event.getId(), event.getTopic(), event.getAggregateId());

                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();

                outboxEventRepository.delete(event);

                log.info("[OUTBOX] Evento {} entregue e removido da tabela.", event.getId());

            } catch (Exception e) {
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastAttemptAt(LocalDateTime.now());

                if (event.getAttemptCount() >= maxAttempts) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("[OUTBOX] Evento {} excedeu o limite de {} tentativas e foi marcado como FAILED. Causa: {}",
                            event.getId(), maxAttempts, e.getMessage());
                } else {
                    log.warn("[OUTBOX] Falha ao enviar evento {} (tentativa {}/{}). Será tentado novamente no próximo ciclo. Causa: {}",
                            event.getId(), event.getAttemptCount(), maxAttempts, e.getMessage());
                }

                outboxEventRepository.save(event);
            }
        }
    }
}
