package br.com.leilao.repository;

import br.com.leilao.domain.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    boolean existsByQuestion_Id(Long questionId);
}