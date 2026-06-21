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
import br.com.leilao.integration.feign.UserClient;
import br.com.leilao.integration.feign.dto.UserResponse;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import br.com.leilao.repository.AnswerRepository;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.AnswerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnswerService
{

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserClient userClient;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AnswerMapper answerMapper;

    @Value("${app.kafka.topics.qa-review-created-pending}")
    private String topic;

    @Transactional
    @CacheEvict(value = "auction_questions", allEntries = true)
    public AnswerResponse createAnswer(Long questionId, UUID userId, boolean allowed, CreateAnswerRequest request)
    {
        if (!allowed) {
            throw new ForbiddenOperationException("Usuário não autorizado a realizar esta operação (conta restrita).");
        }

        Question question = questionRepository.findByIdOrThrow(questionId);

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
                .authorId(userId)
                .text(request.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        answer = answerRepository.save(answer);

        UserResponse seller = userClient.getUserById(userId);

        MessageCreatedPendingReview event = new MessageCreatedPendingReview(
                question.getAuctionId(),
                userId,
                answer.getId(),
                seller.nome(),
                seller.email(),
                answer.getText(),
                Instant.now(),
                UUID.randomUUID()
        );

        outboxEventPublisher.publish(topic, event);

        return answerMapper.toResponse(answer);
    }

    @Transactional
    @CacheEvict(value = "auction_questions", allEntries = true)
    public void deleteAnswer(Long questionId, Long answerId, UUID userId) {
        Answer answer = getAnswerById(answerId);

        if (!answer.getQuestion().getId().equals(questionId)) {
            throw new InvalidOperationException("A resposta não pertence a esta pergunta.");
        }

        if (!answer.getAuthorId().equals(userId)) {
            throw new ForbiddenOperationException("Você não tem permissão para excluir esta resposta.");
        }

        if (answer.getStatus() == ContentStatus.PENDING_ANALYSIS) {
            throw new InvalidOperationException("Não é permitido excluir uma resposta que ainda está em análise.");
        }

        answer.setStatus(ContentStatus.DELETED);
    }

    private Answer getAnswerById(Long answerId)
    {
        return answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Resposta não encontrada."));
    }
}