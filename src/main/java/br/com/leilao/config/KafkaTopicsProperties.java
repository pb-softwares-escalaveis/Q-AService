package br.com.leilao.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.kafka.topics")
@Getter
@Setter
public class KafkaTopicsProperties {
    private String qaReviewCreatedPending;
    private String qaReviewApproved;
    private String qaReviewRejected;
    private String notifyQuestionApproved;
    private String notifyAnswerApproved;
    private String notifyQuestionRejected;
    private String notifyAnswerRejected;
}
