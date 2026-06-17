package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.AnswerApprovedNotification;
import br.com.leilao.integration.kafka.events.AnswerRejectedNotification;
import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import br.com.leilao.integration.kafka.events.QuestionApprovedNotification;
import br.com.leilao.integration.kafka.events.QuestionRejectedNotification;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaReviewConsumer {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Value("${app.kafka.topics.notify-question-approved}")
    private String questionApprovedTopic;
    @Value("${app.kafka.topics.notify-answer-approved}")
    private String answerApprovedTopic;
    @Value("${app.kafka.topics.notify-question-rejected}")
    private String questionRejectedTopic;
    @Value("${app.kafka.topics.notify-answer-rejected}")
    private String answerRejectedTopic;

    /**
     * Aprovação vinda do Review Service. Marca o conteúdo como ACTIVE e, no MESMO
     * commit, grava no Outbox a notificação para o destinatário correto:
     * pergunta aprovada -> vendedor; resposta aprovada -> autor da pergunta (comprador).
     */
    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.qa-review-approved}", groupId = "qa-group")
    public void consumeApproved(MessageReviewApproved event)
    {
        System.out.println("");
        log.info("[KAFKA CONSUMER] Conteúdo aprovado recebido: messageId={}", event.messageId());

        Optional<Question> questionOpt = questionRepository.findById(event.messageId());
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setStatus(ContentStatus.ACTIVE);
            questionRepository.save(question);

            outboxEventPublisher.publish(questionApprovedTopic, new QuestionApprovedNotification(
                    question.getSellerId(),
                    question.getAuctionId(),
                    question.getId(),
                    question.getText(),
                    Instant.now(),
                    event.correlationId()
            ));

            log.info("[KAFKA CONSUMER] Pergunta {} marcada como ACTIVE. Notificação para o vendedor {} enfileirada.",
                    question.getId(), question.getSellerId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(event.messageId());
        if (answerOpt.isPresent()) {
            Answer answer = answerOpt.get();
            answer.setStatus(ContentStatus.ACTIVE);
            answerRepository.save(answer);

            Question question = answer.getQuestion();
            outboxEventPublisher.publish(answerApprovedTopic, new AnswerApprovedNotification(
                    UUID.fromString("bddfe29d-3bd1-47e5-bf4b-03a50c65d534"),  // !!!! AQUI !!!!!
                    question.getAuctionId(),
                    question.getId(),
                    answer.getId(),
                    question.getText(),
                    answer.getText(),
                    Instant.now(),
                    event.correlationId()
            ));

            log.info("[KAFKA CONSUMER] Resposta {} marcada como ACTIVE. Notificação para o comprador {} enfileirada.",
                    answer.getId(), question.getUserId());
            return;
        }

        log.warn("[KAFKA CONSUMER] Recebida aprovação para messageId={} não encontrada no sistema.", event.messageId());
    }

    /**
     * Rejeição vinda do Review Service. Marca o conteúdo como REJECTED e, no MESMO
     * commit, grava no Outbox a notificação para o AUTOR do conteúdo:
     * pergunta rejeitada -> autor da pergunta (comprador); resposta rejeitada -> autor da resposta (vendedor).
     */
    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.qa-review-rejected}", groupId = "qa-group")
    public void consumeRejected(MessageReviewRejected event) {
        log.info("[KAFKA CONSUMER] Conteúdo rejeitado recebido: messageId={} | reason={}", event.messageId(), event.reason());

        Optional<Question> questionOpt = questionRepository.findById(event.messageId());
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setStatus(ContentStatus.REJECTED);
            question.setRejectionReason(event.reason());
            questionRepository.save(question);

            outboxEventPublisher.publish(questionRejectedTopic, new QuestionRejectedNotification(
                    question.getUserId(),
                    question.getAuctionId(),
                    question.getId(),
                    question.getText(),
                    event.reason(),
                    Instant.now(),
                    event.correlationId()
            ));

            log.info("[KAFKA CONSUMER] Pergunta {} marcada como REJECTED. Notificação para o autor {} enfileirada.",
                    question.getId(), question.getUserId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(event.messageId());
        if (answerOpt.isPresent()) {
            Answer answer = answerOpt.get();
            answer.setStatus(ContentStatus.REJECTED);
            answer.setRejectionReason(event.reason());
            answerRepository.save(answer);

            Question question = answer.getQuestion();
            outboxEventPublisher.publish(answerRejectedTopic, new AnswerRejectedNotification(
                    answer.getUserId(),
                    question.getAuctionId(),
                    question.getId(),
                    answer.getId(),
                    answer.getText(),
                    event.reason(),
                    Instant.now(),
                    event.correlationId()
            ));

            log.info("[KAFKA CONSUMER] Resposta {} marcada como REJECTED. Notificação para o autor {} enfileirada.",
                    answer.getId(), answer.getUserId());
            return;
        }

        log.warn("[KAFKA CONSUMER] Recebida rejeição para messageId={} não encontrada no sistema.", event.messageId());
    }
}
