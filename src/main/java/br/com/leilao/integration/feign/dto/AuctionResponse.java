package br.com.leilao.integration.feign.dto;

import java.util.UUID;

public record AuctionResponse(
        Long id,
        UUID sellerId
) {}