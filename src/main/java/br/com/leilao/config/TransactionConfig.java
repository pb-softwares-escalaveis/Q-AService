package br.com.leilao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


 //HTTP (Feign) FORA da transação. Ver QuestionService/AnswerService.
@Configuration
public class TransactionConfig
{
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager)
    {
        return new TransactionTemplate(transactionManager);
    }
}
