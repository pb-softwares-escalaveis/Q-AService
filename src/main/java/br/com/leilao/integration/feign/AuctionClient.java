package br.com.leilao.integration.feign;

import br.com.leilao.integration.feign.dto.AuctionResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auction-service", fallbackFactory = AuctionClientFallbackFactory.class)
@Retry(name = "auction-service")
public interface AuctionClient {
    @GetMapping("/api/auctions/{auctionId}")
    AuctionResponse getAdById(@PathVariable("auctionId") Long auctionId);
}