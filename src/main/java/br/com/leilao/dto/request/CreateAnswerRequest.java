package br.com.leilao.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnswerRequest(
        @NotBlank(message = "O texto da resposta é obrigatório")
        @Size(max = 2000, message = "A resposta pode ter no máximo 2000 caracteres")
        String text
) {}