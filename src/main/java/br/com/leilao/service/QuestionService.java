package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.exception.InvalidOperationException;
import br.com.leilao.integration.feign.AuctionClient;
import br.com.leilao.integration.feign.UserClient;
import br.com.leilao.integration.feign.dto.AuctionResponse;
import br.com.leilao.integration.feign.dto.UserResponse;
import br.com.leilao.integration.kafka.OutboxEventPublisher;
import br.com.leilao.integration.kafka.events.MessageCreatedPendingReview;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.QuestionMapper;
import br.com.leilao.config.KafkaTopicsProperties;
import org.springframework.cache.CacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final CacheManager cacheManager;

    @CacheEvict(value = "auction_questions", allEntries = true)
    public QuestionResponse createQuestion(Long auctionId, UUID userId, boolean allowed, CreateQuestionRequest request)
    {
        if (!allowed) {
            throw new ForbiddenOperationException("Usuário não autorizado a realizar esta operação (conta restrita).");
        }

        // Chamadas feign ficam FORA da transação para não segurar conexão do enquanto aguarda os serviços externos (auction-service / user-service).
        AuctionResponse auctionResponse = auctionClient.getAuctionById(auctionId);
        if (auctionResponse.sellerId().equals(userId)) {
            throw new InvalidOperationException("Você não pode fazer perguntas no seu próprio anúncio.");
        }

        UserResponse author = userClient.getUserById(userId);

        return transactionTemplate.execute(status -> {
            Question question = Question.builder()
                    .auctionId(auctionId)
                    .sellerId(auctionResponse.sellerId())
                    .authorId(userId)
                    .text(request.text())
                    .status(ContentStatus.PENDING_ANALYSIS)
                    .build();

            question = questionRepository.save(question);

            MessageCreatedPendingReview event = new MessageCreatedPendingReview(
                    auctionId,
                    question.getSellerId(),
                    question.getId(),
                    author.nome(),
                    author.email(),
                    question.getText(),
                    Instant.now(),
                    userId
            );

            outboxEventPublisher.publish(kafkaTopicsProperties.getQaReviewCreatedPending(), String.valueOf(auctionId), event);

            return questionMapper.toResponse(question);
        });
    }

    @Transactional
    public void deleteQuestion(Long questionId, UUID userId)
    {
        Question question = questionRepository.findByIdOrThrow(questionId);

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

        // Listagem é cacheada por (auctionId-página); limpamos todas as entradas para
        // não deixar página stale (evict por chave única não cobre todas as páginas).
        var cache = cacheManager.getCache("auction_questions");
        if (cache != null) {
            cache.clear();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "auction_questions", key = "#auctionId + '-' + #pageable.pageNumber")
    public Page<QuestionResponse> listActiveQuestions(Long auctionId, Pageable pageable)
    {
        Page<Question> questionsPage = questionRepository.findByAuctionIdAndStatus(auctionId, ContentStatus.ACTIVE, pageable);
        List<QuestionResponse> content = questionsPage.getContent().stream()
                .map(questionMapper::toResponse)
                .toList();

        return new br.com.leilao.dto.response.RestResponsePage<>(content, pageable, questionsPage.getTotalElements());
    }
}
