package br.com.leilao.integration.feign;

import br.com.leilao.integration.feign.dto.AuctionResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "auctionService",
        fallbackFactory = AuctionClientFallbackFactory.class
)
@Retry(name = "auctionService")
public interface AuctionClient {
    @GetMapping("/auctions/{auctionId}")
    AuctionResponse getAuctionById(@PathVariable("auctionId") Long auctionId);
}