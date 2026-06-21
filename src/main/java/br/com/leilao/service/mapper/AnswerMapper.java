package br.com.leilao.service.mapper;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.dto.response.AnswerResponse;
import org.springframework.stereotype.Component;

@Component
public class AnswerMapper
{
    public AnswerResponse toResponse(Answer a)
    {
        return new AnswerResponse(
                a.getId(),
                a.getQuestion().getId(),
                a.getAuthorId(),
                a.getText(),
                a.getStatus(),
                a.getRejectionReason(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}