package br.com.leilao.integration.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class NotificationPublisherMock implements NotificationPublisher {

    @Override
    public void notifySellerNewQuestion(UUID sellerId, UUID questionId, UUID adId) {
        log.info("[MOCK] Notificando vendedor {} sobre nova pergunta {} no anúncio {}", sellerId, questionId, adId);
    }

    @Override
    public void notifyBuyerNewAnswer(UUID buyerId, UUID questionId, UUID adId) {
        log.info("[MOCK] Notificando comprador {} sobre nova resposta na pergunta {} do anúncio {}", buyerId, questionId, adId);
    }
}