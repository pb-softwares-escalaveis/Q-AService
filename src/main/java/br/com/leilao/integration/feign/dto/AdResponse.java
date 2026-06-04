package br.com.leilao.integration.feign.dto;

import java.util.UUID;

public record AdResponse(UUID id, UUID sellerId) {}