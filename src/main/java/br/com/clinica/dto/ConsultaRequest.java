package br.com.clinica.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ConsultaRequest(

        @NotNull(message = "O paciente é obrigatório")
        Long pacienteId,

        @NotNull(message = "O médico é obrigatório")
        Long medicoId,

        @NotNull(message = "A data/hora é obrigatória")
        @Future(message = "A consulta deve ser marcada para uma data futura")
        LocalDateTime dataHora,

        @Size(max = 255, message = "Observações podem ter no máximo 255 caracteres")
        String observacoes
) {
}
