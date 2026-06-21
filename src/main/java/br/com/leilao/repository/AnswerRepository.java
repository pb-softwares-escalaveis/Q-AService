package br.com.leilao.repository;

import br.com.leilao.domain.entity.Answer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long>
{
    @EntityGraph(attributePaths = {"question"})
    @Override
    Optional<Answer> findById(Long id);

    boolean existsByQuestion_Id(Long questionId);
}