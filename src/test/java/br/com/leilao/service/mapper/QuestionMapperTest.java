package br.com.leilao.service.mapper;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.dto.response.QuestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


// regra só Answer ACTIVE aparece publicamente na listagem.

class QuestionMapperTest
{
    private QuestionMapper mapper;
    private Question question;

    @BeforeEach
    void setUp()
    {
        mapper = new QuestionMapper(new AnswerMapper());

        question = Question.builder()
                .id(100L)
                .auctionId(1L)
                .sellerId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .text("O produto é original?")
                .status(ContentStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Pergunta sem resposta -> response.answer = null")
    void semResposta()
    {
        QuestionResponse response = mapper.toResponse(question);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertNull(response.answer());
    }

    @Test
    @DisplayName("Pergunta com resposta ACTIVE -> response.answer presente")
    void respostaActiveAparece()
    {
        question.setAnswer(answerWithStatus(ContentStatus.ACTIVE));

        QuestionResponse response = mapper.toResponse(question);

        assertNotNull(response.answer());
        assertEquals(200L, response.answer().id());
        assertEquals("Resposta", response.answer().text());
    }

    @Test
    @DisplayName("Pergunta com resposta PENDING_ANALYSIS -> response.answer = null (não vaza na listagem)")
    void respostaPendingNaoAparece()
    {
        question.setAnswer(answerWithStatus(ContentStatus.PENDING_ANALYSIS));

        QuestionResponse response = mapper.toResponse(question);

        assertNull(response.answer());
    }

    @Test
    @DisplayName("Pergunta com resposta REJECTED -> response.answer = null")
    void respostaRejectedNaoAparece()
    {
        question.setAnswer(answerWithStatus(ContentStatus.REJECTED));

        QuestionResponse response = mapper.toResponse(question);

        assertNull(response.answer());
    }

    @Test
    @DisplayName("Pergunta com resposta DELETED -> response.answer = null")
    void respostaDeletedNaoAparece()
    {
        question.setAnswer(answerWithStatus(ContentStatus.DELETED));

        QuestionResponse response = mapper.toResponse(question);

        assertNull(response.answer());
    }

    private Answer answerWithStatus(ContentStatus status)
    {
        return Answer.builder()
                .id(200L)
                .question(question)
                .authorId(UUID.randomUUID())
                .text("Resposta")
                .status(status)
                .build();
    }
}
