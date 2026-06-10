package br.com.leilao.integration.feign;

import br.com.leilao.exception.AuctionServiceUnavailableException;
import br.com.leilao.integration.feign.dto.AuctionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuctionClientFallbackFactory implements FallbackFactory<AuctionClient> {

    @Override
    public AuctionClient create(Throwable cause) {
        return new AuctionClient() {
            @Override
            public AuctionResponse getAdById(Long auctionId) {
                log.error("Erro ao buscar o anúncio {}: {}", auctionId, cause.getMessage());
                throw new AuctionServiceUnavailableException("O serviço de leilões está temporariamente indisponível.", cause);
            }
        };
    }
}