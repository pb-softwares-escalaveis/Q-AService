package br.com.leilao.integration.notification;

import java.util.UUID;

public interface NotificationPublisher {
    void notifySellerNewQuestion(UUID sellerId, UUID questionId, UUID adId);
    void notifyBuyerNewAnswer(UUID buyerId, UUID questionId, UUID adId);
}