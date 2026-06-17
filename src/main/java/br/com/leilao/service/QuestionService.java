package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.exception.InvalidOperationException;
import br.com.leilao.exception.ResourceNotFoundException;
import br.com.leilao.integration.feign.AuctionClient;
import br.com.leilao.integration.feign.UserClient;
import br.com.leilao.integration.feign.dto.AuctionResponse;
import br.com.leilao.integration.feign.dto.UserResponse;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService
{

    private final QuestionRepository questionRepository;
    private final AuctionClient auctionClient;
    private final UserClient userClient;
    private final OutboxEventPublisher outboxEventPublisher;
    private final QuestionMapper questionMapper;

    @Value("${app.kafka.topics.qa-review-created-pending}")
    private String topic;

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public QuestionResponse createQuestion(Long auctionId, UUID userId, boolean allowed, CreateQuestionRequest request)
    {
        if (!allowed) {
            throw new ForbiddenOperationException("Usuário não autorizado a realizar esta operação (conta restrita).");
        }

        AuctionResponse auctionResponse = auctionClient.getAuctionById(auctionId);

        Question question = Question.builder()
                .auctionId(auctionId)
                .sellerId(auctionResponse.sellerId())
                .authorId(userId)
                .text(request.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        question = questionRepository.save(question);

        UserResponse seller = userClient.getUserById(question.getSellerId());

        MessageCreatedPendingReview event = new MessageCreatedPendingReview(
                auctionId,
                question.getSellerId(),
                question.getId(),
                seller.nome(),
                seller.email(),
                question.getText(),
                Instant.now(),
                UUID.randomUUID()
        );

        outboxEventPublisher.publish(topic, event);

        return questionMapper.toResponse(question);
    }

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public void deleteQuestion(Long questionId, UUID userId)
    {
        Question question = getQuestionById(questionId);

        if (!question.getAuthorId().equals(userId)) {
            throw new ForbiddenOperationException("Você não tem permissão para excluir esta pergunta.");
        }

        if (question.getStatus() == ContentStatus.PENDING_ANALYSIS) {
            throw new InvalidOperationException("Não é permitido excluir uma pergunta que ainda está em análise.");
        }

        question.setStatus(ContentStatus.DELETED);
        if (question.getAnswer() != null) {
            question.getAnswer().setStatus(ContentStatus.DELETED);
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "ad_questions", key = "#auctionId + '-' + #pageable.pageNumber")
    public Page<QuestionResponse> listActiveQuestions(Long auctionId, Pageable pageable)
    {
        Page<Question> questionsPage = questionRepository.findByAuctionIdAndStatus(auctionId, ContentStatus.ACTIVE, pageable);
        List<QuestionResponse> content = questionsPage.getContent().stream()
                .map(questionMapper::toResponse)
                .toList();

        return new br.com.leilao.dto.response.RestResponsePage<>(content, pageable, questionsPage.getTotalElements());
    }

    public Question getQuestionById(Long questionId)
    {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pergunta não encontrada."));
    }
}