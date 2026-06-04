package br.com.leilao.service;

import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.request.CreateQuestionRequest;
import br.com.leilao.dto.request.UpdateQuestionRequest;
import br.com.leilao.dto.response.QuestionResponse;
import br.com.leilao.exception.ForbiddenOperationException;
import br.com.leilao.exception.InvalidOperationException;
import br.com.leilao.exception.ResourceNotFoundException;
import br.com.leilao.integration.feign.AdClient;
import br.com.leilao.integration.feign.dto.AdResponse;
import br.com.leilao.integration.notification.NotificationPublisher;
import br.com.leilao.repository.QuestionRepository;
import br.com.leilao.service.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AdClient adClient;
    private final ContentAnalysisMockService contentAnalysisServiceMock;
    private final NotificationPublisher notificationPublisher;
    private final QuestionMapper questionMapper;

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public QuestionResponse createQuestion(UUID adId, UUID userId, CreateQuestionRequest request)
    {
        AdResponse adResponse = adClient.getAdById(adId);

        Question question = Question.builder()
                .adId(adId)
                .sellerId(adResponse.sellerId())
                .userId(userId)
                .text(request.text())
                .status(ContentStatus.PENDING_ANALYSIS)
                .build();

        question = questionRepository.save(question);

        contentAnalysisServiceMock.analyze(question);
        notificationPublisher.notifySellerNewQuestion(question.getSellerId(), question.getId(), adId);

        return questionMapper.toResponse(question);
    }

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public QuestionResponse updateQuestion(UUID questionId, UUID userId, UpdateQuestionRequest request)
    {
        Question question = getQuestionById(questionId);

        if (!question.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Você não tem permissão para editar esta pergunta.");
        }

        if (question.getStatus() == ContentStatus.DELETED) {
            throw new InvalidOperationException("Não é possível editar uma pergunta deletada.");
        }

        question.setText(request.text());
        question.setStatus(ContentStatus.PENDING_ANALYSIS);
        question.setRejectionReason(null);

        contentAnalysisServiceMock.analyze(question);

        return questionMapper.toResponse(question);
    }

    @Transactional
    @CacheEvict(value = "ad_questions", allEntries = true)
    public void deleteQuestion(UUID questionId, UUID userId)
    {
        Question question = getQuestionById(questionId);

        if (!question.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Você não tem permissão para excluir esta pergunta.");
        }

        question.setStatus(ContentStatus.DELETED);
        if (question.getAnswer() != null) {
            question.getAnswer().setStatus(ContentStatus.DELETED);
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "ad_questions", key = "#adId + '-' + #pageable.pageNumber")
    public Page<QuestionResponse> listActiveQuestions(UUID adId, Pageable pageable)
    {
        Page<Question> questionsPage = questionRepository.findByAdIdAndStatus(adId, ContentStatus.ACTIVE, pageable);
        List<QuestionResponse> content = questionsPage.getContent().stream()
                .map(questionMapper::toResponse)
                .toList();

        return new br.com.leilao.dto.response.RestResponsePage<>(content, pageable, questionsPage.getTotalElements());
    }

    public Question getQuestionById(UUID questionId)
    {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Pergunta não encontrada."));
    }
}