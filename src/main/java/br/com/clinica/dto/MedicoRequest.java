package br.com.clinica.dto;

import br.com.clinica.model.Especialidade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MedicoRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        String nome,

        @NotBlank(message = "O CRM é obrigatório")
        @Pattern(regexp = "\\d{4,6}-[A-Z]{2}", message = "CRM deve estar no formato 123456-SP")
        String crm,

        @NotNull(message = "A especialidade é obrigatória")
        Especialidade especialidade
) {
}
