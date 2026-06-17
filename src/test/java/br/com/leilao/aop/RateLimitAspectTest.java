package br.com.leilao.aop;

import br.com.leilao.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RateLimited annotation;

    private RateLimitProperties properties;

    @InjectMocks
    private RateLimitAspect aspect;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        properties = new RateLimitProperties();
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setLimit(10);
        rule.setWindowSeconds(60);
        properties.setActions(Map.of("create-question", rule));

        aspect = new RateLimitAspect(redisTemplate, properties);

        when(annotation.value()).thenReturn("create-question");
    }

    @Test
    @DisplayName("Primeira chamada — INCR retorna 1, define EXPIRE e deixa proceder")
    void primeiraChamadaSetaExpire() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, userId, true});
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:qa:create-question:" + userId)).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.enforce(joinPoint, annotation);

        assertEquals("OK", result);
        verify(redisTemplate).expire("rl:qa:create-question:" + userId, Duration.ofSeconds(60));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Dentro do limite — não seta EXPIRE de novo e deixa proceder")
    void dentroDoLimiteNaoReseta() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, userId});
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(5L);
        when(joinPoint.proceed()).thenReturn("OK");

        aspect.enforce(joinPoint, annotation);

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Acima do limite — lança RateLimitExceededException e não procede")
    void acimaDoLimiteBloqueia() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, userId});
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(11L);

        assertThrows(RateLimitExceededException.class, () -> aspect.enforce(joinPoint, annotation));
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Redis indisponível — fail-open: deixa proceder")
    void redisDownFailOpen() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, userId});
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenThrow(new QueryTimeoutException("redis down"));
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.enforce(joinPoint, annotation);

        assertEquals("OK", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Ação sem regra configurada — log + deixa proceder")
    void semRegraDeixaProceder() throws Throwable {
        when(annotation.value()).thenReturn("acao-inexistente");
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.enforce(joinPoint, annotation);

        assertEquals("OK", result);
        verify(redisTemplate, never()).opsForValue();
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Sem UUID nos args — log + deixa proceder")
    void semUuidDeixaProceder() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, "no-uuid-here"});
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.enforce(joinPoint, annotation);

        assertEquals("OK", result);
        verify(redisTemplate, never()).opsForValue();
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Usa a chave correta com prefixo rl:qa: + action + userId")
    void usaChaveCorreta() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("OK");

        aspect.enforce(joinPoint, annotation);

        verify(valueOps).increment("rl:qa:create-question:" + userId);
        verify(redisTemplate).expire(eq("rl:qa:create-question:" + userId), any(Duration.class));
    }
}
