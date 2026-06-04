package br.com.leilao.integration.feign;

import br.com.leilao.exception.AdServiceUnavailableException;
import br.com.leilao.integration.feign.dto.AdResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class AdClientFallbackFactory implements FallbackFactory<AdClient> {

    @Override
    public AdClient create(Throwable cause) {
        return new AdClient() {
            @Override
            public AdResponse getAdById(UUID adId) {
                log.error("[FEIGN-FALLBACK] Falha ao buscar anúncio {} no ad-service. Motivo: {}", adId, cause.getMessage());
                throw new AdServiceUnavailableException("O serviço de anúncios está temporariamente indisponível.", cause);
            }
        };
    }
}