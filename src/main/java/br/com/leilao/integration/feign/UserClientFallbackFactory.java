package br.com.leilao.integration.feign;

import br.com.leilao.integration.feign.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback degradado (modo 7b): se o user-service estiver indisponível, devolve
 * nome/e-mail nulos em vez de bloquear a publicação do evento de moderação.
 * Os campos serão removidos do contrato MessageCreatedPendingReview na Fase 2.
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public UserResponse getUserById(UUID userId) {
                log.warn("[USER-CLIENT] Indisponível para userId={}. Degradando para nome/e-mail nulos. Causa: {}",
                        userId, cause.getMessage());
                return new UserResponse(null, null);
            }
        };
    }
}
