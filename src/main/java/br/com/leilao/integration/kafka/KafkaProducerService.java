package br.com.leilao.integration.kafka;

import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.qa-created-pending}")
    private String topic;

    public void sendForReview(MessageCreatedPendingReview event) {
        log.info("[KAFKA PRODUCER] Enviando conteúdo para revisão: messageId={} | auctionId={} | topic={}", 
                event.messageId(), event.auctionId(), topic);
        
        try {
            kafkaTemplate.send(topic, event.messageId().toString(), event);
        } catch (Exception e) {
            log.error("[KAFKA PRODUCER] Erro ao enviar mensagem para o Kafka: {}", e.getMessage());
            // TODO: implementar um fallback ou retry aqui
        }
    }
}
