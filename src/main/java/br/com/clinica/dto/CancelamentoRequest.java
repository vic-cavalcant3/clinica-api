package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoRequest(

        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(max = 255, message = "O motivo pode ter no máximo 255 caracteres")
        String motivo
) {
}
