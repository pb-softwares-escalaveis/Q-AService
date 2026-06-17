package br.com.leilao.aop;

import br.com.leilao.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Around("@annotation(rateLimited)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String action = rateLimited.value();
        RateLimitProperties.Rule rule = properties.getActions().get(action);
        if (rule == null) {
            log.warn("[RATE-LIMIT] Ação '{}' anotada com @RateLimited mas sem configuração em app.rate-limit.actions.{} — chamada liberada.", action, action);
            return joinPoint.proceed();
        }

        UUID userId = findUserId(joinPoint.getArgs());
        if (userId == null) {
            log.warn("[RATE-LIMIT] Nenhum UUID encontrado nos args de '{}' — chamada liberada.", action);
            return joinPoint.proceed();
        }

        String key = "rl:qa:" + action + ":" + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(rule.getWindowSeconds()));
            }
            if (count != null && count > rule.getLimit()) {
                log.info("[RATE-LIMIT] Bloqueado userId={} action={} count={} limit={}", userId, action, count, rule.getLimit());
                throw new RateLimitExceededException(
                        "Limite de " + rule.getLimit() + " requisições por " + rule.getWindowSeconds() + "s atingido. Tente novamente em breve."
                );
            }
        } catch (DataAccessException e) {
            log.warn("[RATE-LIMIT] Redis indisponível em '{}' para userId={} — fail-open. Causa: {}", action, userId, e.getMessage());
        }

        return joinPoint.proceed();
    }

    private UUID findUserId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }
}
