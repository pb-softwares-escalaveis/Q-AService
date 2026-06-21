package br.com.leilao.service.mapper;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.dto.response.AnswerResponse;
import br.com.leilao.dto.response.QuestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionMapper {

    private final AnswerMapper answerMapper;

    public QuestionResponse toResponse(Question q) {
        Answer visible = q.publicAnswer();
        AnswerResponse answerResponse = visible != null ? answerMapper.toResponse(visible) : null;

        return new QuestionResponse(
                q.getId(),
                q.getAuctionId(),
                q.getAuthorId(),
                q.getText(),
                q.getStatus(),
                q.getRejectionReason(),
                q.getCreatedAt(),
                q.getUpdatedAt(),
                answerResponse
        );
    }
}