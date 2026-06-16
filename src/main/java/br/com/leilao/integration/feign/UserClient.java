package br.com.leilao.integration.feign;

import br.com.leilao.integration.feign.dto.UserResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
@Retry(name = "user-service")
public interface UserClient {

    @GetMapping("/usuarios/{userId}")
    UserResponse getUserById(@PathVariable("userId") UUID userId);
}
