package br.com.leilao.integration.kafka.consumer;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.integration.kafka.events.MessageReviewApproved;
import br.com.leilao.integration.kafka.events.MessageReviewRejected;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaReviewConsumer {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.qa-review-approved}", groupId = "qa-group")
    public void consumeApproved(MessageReviewApproved event)
    {
        log.info("[KAFKA CONSUMER] Conteúdo aprovado recebido: messageId={}", event.messageId());

        Optional<Question> questionOpt = questionRepository.findById(event.messageId());
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setStatus(ContentStatus.ACTIVE);
            questionRepository.save(question);
            
            log.info("[KAFKA CONSUMER] Pergunta {} marcada como ACTIVE.", question.getId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(event.messageId());
        if (answerOpt.isPresent()) {
            Answer answer = answerOpt.get();
            answer.setStatus(ContentStatus.ACTIVE);
            answerRepository.save(answer);
            
            log.info("[KAFKA CONSUMER] Resposta {} marcada como ACTIVE.", answer.getId());
            return;
        }

        log.warn("[KAFKA CONSUMER] Recebida aprovação para messageId={} não encontrada no sistema.", event.messageId());
    }

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.qa-review-rejected}", groupId = "qa-group")
    public void consumeRejected(MessageReviewRejected event)
    {
        log.info("[KAFKA CONSUMER] Conteúdo rejeitado recebido: messageId={} | reason={}", event.messageId(), event.reason());

        Optional<Question> questionOpt = questionRepository.findById(event.messageId());
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setStatus(ContentStatus.REJECTED);
            question.setRejectionReason(event.reason());
            questionRepository.save(question);
            log.info("[KAFKA CONSUMER] Pergunta {} marcada como REJECTED.", question.getId());
            return;
        }

        Optional<Answer> answerOpt = answerRepository.findById(event.messageId());
        if (answerOpt.isPresent()) {
            Answer answer = answerOpt.get();
            answer.setStatus(ContentStatus.REJECTED);
            answer.setRejectionReason(event.reason());
            answerRepository.save(answer);
            log.info("[KAFKA CONSUMER] Resposta {} marcada como REJECTED.", answer.getId());
            return;
        }

        log.warn("[KAFKA CONSUMER] Recebida rejeição para messageId={} não encontrada no sistema.", event.messageId());
    }
}