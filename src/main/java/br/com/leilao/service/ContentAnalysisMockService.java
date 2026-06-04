package br.com.leilao.service;

import br.com.leilao.domain.entity.Answer;
import br.com.leilao.domain.entity.Question;
import br.com.leilao.domain.enums.ContentStatus;
import br.com.leilao.domain.enums.RejectionReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ContentAnalysisMockService {

    private static final List<String> PROHIBITED_WORDS = List.of("teste-rejeitado", "ofensa", "spam");

    public void analyze(Question question) {
        log.info("[MOCK] Analisando pergunta ID: {}", question.getId());
        if (containsProhibitedWords(question.getText())) {
            question.setStatus(ContentStatus.REJECTED);
            question.setRejectionReason(RejectionReason.OFFENSIVE);
        } else {
            question.setStatus(ContentStatus.ACTIVE);
            question.setRejectionReason(null);
        }
    }

    public void analyze(Answer answer) {
        log.info("[MOCK] Analisando resposta ID: {}", answer.getId());
        if (containsProhibitedWords(answer.getText())) {
            answer.setStatus(ContentStatus.REJECTED);
            answer.setRejectionReason(RejectionReason.OFFENSIVE);
        } else {
            answer.setStatus(ContentStatus.ACTIVE);
            answer.setRejectionReason(null);
        }
    }

    private boolean containsProhibitedWords(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        return PROHIBITED_WORDS.stream().anyMatch(lowerText::contains);
    }
}