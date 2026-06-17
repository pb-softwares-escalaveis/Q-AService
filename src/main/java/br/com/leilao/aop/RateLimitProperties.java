package br.com.leilao.aop;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private Map<String, Rule> actions = new HashMap<>();

    @Getter
    @Setter
    public static class Rule {
        private int limit;
        private int windowSeconds;
    }
}
