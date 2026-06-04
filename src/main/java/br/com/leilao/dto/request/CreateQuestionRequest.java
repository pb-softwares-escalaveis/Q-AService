package br.com.leilao.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
        @NotBlank(message = "O texto da pergunta é obrigatório")
        @Size(max = 1000, message = "A pergunta pode ter no máximo 1000 caracteres")
        String text
) {}