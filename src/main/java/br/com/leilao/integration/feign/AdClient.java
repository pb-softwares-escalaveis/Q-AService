package br.com.leilao.integration.feign;

import br.com.leilao.integration.feign.dto.AdResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "ad-service", fallbackFactory = AdClientFallbackFactory.class)
@Retry(name = "ad-service")
public interface AdClient {
    @GetMapping("/api/ads/{adId}")
    AdResponse getAdById(@PathVariable("adId") UUID adId);
}