package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.AnswerApprovedNotification;
import br.com.leilao.integration.kafka.events.AnswerRejectedNotification;
import br.com.leilao.integration.kafka.events.QuestionApprovedNotification;
import br.com.leilao.integration.kafka.events.QuestionRejectedNotification;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import br.com.leilao.config.KafkaTopicsProperties;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewResultService
{
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final CacheManager cacheManager;

    /**
     * Marca o conteúdo como ACTIVE e enfileira a notificação:
     * pergunta aprovada -> vendedor; resposta aprovada -> autor da pergunta (comprador).
     */
    @Transactional
    public void applyApproval(Long messageId, UUID correlationId)
    {
        Optional<Question> questionOpt = questionRepository.findById(messageId);
        if (questionOpt.isPresent())
        {
            Question question = questionOpt.get();
            if (question.getStatus() != ContentStatus.PENDING_ANALYSIS) {
                log.warn("[REVIEW-RESULT] Ignorando aprovação para Pergunta {} — status atual {} (esperado PENDING_ANALYSIS). Possível redelivery do Kafka.",
                        question.getId(), question.getStatus());
                return;
            }

            question.setStatus(ContentStatus.ACTIVE);
            questionRepository.save(question);

            outboxEventPublisher.publish(kafkaTopicsProperties.getNotifyQuestionApproved(),
                    String.valueOf(question.getAuctionId()),
                    QuestionApprovedNotification.forSellerOf(question, correlationId));

            var cache = cacheManager.getCache("auction_questions");
            if (cache != null) {
                cache.evict(question.getAuctionId());
            }

            log.info("[REVIEW-RESULT] Pergunta {} marcada como ACTIVE. Notificação para o vendedor {} enfileirada.",
                    question.getId(), question.getSellerId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(messageId);
        if (answerOpt.isPresent())
        {
            Answer answer = answerOpt.get();
            if (answer.getStatus() != ContentStatus.PENDING_ANALYSIS) {
                log.warn("[REVIEW-RESULT] Ignorando aprovação para Resposta {} — status atual {} (esperado PENDING_ANALYSIS). Possível redelivery do Kafka.",
                        answer.getId(), answer.getStatus());
                return;
            }

            answer.setStatus(ContentStatus.ACTIVE);
            answerRepository.save(answer);

            outboxEventPublisher.publish(kafkaTopicsProperties.getNotifyAnswerApproved(),
                    String.valueOf(answer.getQuestion().getAuctionId()),
                    AnswerApprovedNotification.forBuyerOf(answer, correlationId));

            var cache = cacheManager.getCache("auction_questions");
            if (cache != null) {
                cache.evict(answer.getQuestion().getAuctionId());
            }

            log.info("[REVIEW-RESULT] Resposta {} marcada como ACTIVE. Notificação para o comprador {} enfileirada.",
                    answer.getId(), answer.getQuestion().getAuthorId());
            return;
        }

        log.warn("[REVIEW-RESULT] Recebida aprovação para messageId={} não encontrada no sistema.", messageId);
    }

    /**
     * Marca o conteúdo como REJECTED, grava o motivo e enfileira a notificação:
     * pergunta rejeitada -> autor da pergunta (comprador); resposta rejeitada -> autor da resposta (vendedor).
     */
    @Transactional
    public void applyRejection(Long messageId, String reason, UUID correlationId)
    {
        Optional<Question> questionOpt = questionRepository.findById(messageId);
        if (questionOpt.isPresent())
        {
            Question question = questionOpt.get();
            if (question.getStatus() != ContentStatus.PENDING_ANALYSIS) {
                log.warn("[REVIEW-RESULT] Ignorando rejeição para Pergunta {} — status atual {} (esperado PENDING_ANALYSIS). Possível redelivery do Kafka.",
                        question.getId(), question.getStatus());
                return;
            }

            question.setStatus(ContentStatus.REJECTED);
            question.setRejectionReason(reason);
            questionRepository.save(question);

            outboxEventPublisher.publish(kafkaTopicsProperties.getNotifyQuestionRejected(),
                    String.valueOf(question.getAuctionId()),
                    QuestionRejectedNotification.forAuthorOf(question, reason, correlationId));

            var cache = cacheManager.getCache("auction_questions");
            if (cache != null) {
                cache.evict(question.getAuctionId());
            }

            log.info("[REVIEW-RESULT] Pergunta {} marcada como REJECTED. Notificação para o autor {} enfileirada.",
                    question.getId(), question.getAuthorId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(messageId);
        if (answerOpt.isPresent()) {
            Answer answer = answerOpt.get();
            if (answer.getStatus() != ContentStatus.PENDING_ANALYSIS) {
                log.warn("[REVIEW-RESULT] Ignorando rejeição para Resposta {} — status atual {} (esperado PENDING_ANALYSIS). Possível redelivery do Kafka.",
                        answer.getId(), answer.getStatus());
                return;
            }

            answer.setStatus(ContentStatus.REJECTED);
            answer.setRejectionReason(reason);
            answerRepository.save(answer);

            outboxEventPublisher.publish(kafkaTopicsProperties.getNotifyAnswerRejected(),
                    String.valueOf(answer.getQuestion().getAuctionId()),
                    AnswerRejectedNotification.forAuthorOf(answer, reason, correlationId));

            var cache = cacheManager.getCache("auction_questions");
            if (cache != null) {
                cache.evict(answer.getQuestion().getAuctionId());
            }

            log.info("[REVIEW-RESULT] Resposta {} marcada como REJECTED. Notificação para o autor {} enfileirada.",
                    answer.getId(), answer.getAuthorId());
            return;
        }

        log.warn("[REVIEW-RESULT] Recebida rejeição para messageId={} não encontrada no sistema.", messageId);
    }
}
