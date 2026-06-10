package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateAnswerRequest;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.exception.AnswerAlreadyExistsException;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.exception.InvalidOperationException;
import br.com.leilao.exception.ResourceNotFoundException;
import br.com.leilao.integration.kafka.KafkaProducerService;
import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import br.com.leilao.integration.notification.NotificationPublisher;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.service.mapper.AnswerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionService questionService;
    private final KafkaProducerService kafkaProducerService;
    private final NotificationPublisher notificationPublisher;
    private final AnswerMapper answerMapper;

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public AnswerResponse createAnswer(UUID questionId, UUID userId, CreateAnswerRequest request)
    {
        Question question = questionService.getQuestionById(questionId);

        if (!question.getSellerId().equals(userId)) {
            throw new ForbiddenOperationException("Apenas o vendedor do anúncio pode responder a pergunta.");
        }

        if (question.getStatus() != ContentStatus.ACTIVE) {
            throw new InvalidOperationException("Não é possível responder a uma pergunta que não está ativa.");
        }

        if (answerRepository.existsByQuestion_Id(questionId)) {
            throw new AnswerAlreadyExistsException("Já existe uma resposta para esta pergunta.");
        }

        Answer answer = Answer.builder()
                .question(question)
                .userId(userId)
                .text(request.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        answer = answerRepository.save(answer);

        MessageCreatedPendingReview event = new MessageCreatedPendingReview(
                question.getAuctionId(),
                userId,
                answer.getId(),
                "Seller Name Placeholder", // TODO: No futuro, obter detalhes do vendedor
                "seller@email.com",         // TODO: No futuro, obter detalhes do vendedor
                answer.getText(),
                Instant.now(),
                UUID.randomUUID()
        );

        kafkaProducerService.sendForReview(event);

        return answerMapper.toResponse(answer);
    }

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public void deleteAnswer(UUID questionId, UUID answerId, UUID userId) {
        Answer answer = getAnswerById(answerId);

        if (!answer.getQuestion().getId().equals(questionId)) {
            throw new InvalidOperationException("A resposta não pertence a esta pergunta.");
        }

        if (!answer.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Você não tem permissão para excluir esta resposta.");
        }

        if (answer.getStatus() == ContentStatus.PENDING_ANALYSIS) {
            throw new InvalidOperationException("Não é permitido excluir uma resposta que ainda está em análise.");
        }

        answer.setStatus(ContentStatus.DELETED);
    }



    private Answer getAnswerById(UUID answerId)
    {
        return answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Resposta não encontrada."));
    }
}