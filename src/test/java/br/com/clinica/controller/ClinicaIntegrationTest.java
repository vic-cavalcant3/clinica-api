package br.com.clinica.controller;

import br.com.clinica.repository.ConsultaRepository;
import br.com.clinica.repository.MedicoRepository;
import br.com.clinica.repository.PacienteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração: sobem a aplicação inteira com banco H2 em memória
 * e fazem requisições HTTP de verdade nos endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicaIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ConsultaRepository consultaRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private MedicoRepository medicoRepository;

    @BeforeEach
    void limparBanco() {
        consultaRepository.deleteAll();
        pacienteRepository.deleteAll();
        medicoRepository.deleteAll();
    }

    private static final String PACIENTE_JSON = """
            {"nome":"Ana Souza","cpf":"52998224725","email":"ana@email.com",
             "telefone":"11999999999","dataNascimento":"2000-01-01"}
            """;

    private static final String MEDICO_JSON = """
            {"nome":"Dr. Carlos","crm":"123456-SP","especialidade":"CARDIOLOGIA"}
            """;

    private long criar(String url, String json) throws Exception {
        String resposta = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(resposta);
        return node.get("id").asLong();
    }

    private String proximaSegunda(int hora) {
        return LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(hora, 0).toString();
    }

    @Test
    @DisplayName("CRUD completo de paciente")
    void crudPaciente() throws Exception {
        long id = criar("/pacientes", PACIENTE_JSON);

        mvc.perform(get("/pacientes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"));

        mvc.perform(put("/pacientes/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(PACIENTE_JSON.replace("Ana Souza", "Ana Lima")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Lima"));

        mvc.perform(get("/pacientes?nome=lima"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/pacientes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/pacientes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve retornar 400 com os campos inválidos")
    void validacaoPaciente() throws Exception {
        String invalido = """
                {"nome":"A","cpf":"11111111111","email":"nao-e-email","dataNascimento":"2999-01-01"}
                """;
        mvc.perform(post("/pacientes").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.cpf").value("CPF inválido"))
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.dataNascimento").exists());
    }

    @Test
    @DisplayName("Deve retornar 422 ao cadastrar CPF duplicado")
    void cpfDuplicado() throws Exception {
        criar("/pacientes", PACIENTE_JSON);
        mvc.perform(post("/pacientes").contentType(MediaType.APPLICATION_JSON).content(PACIENTE_JSON))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Fluxo de consulta: agendar, bloquear conflito, cancelar")
    void fluxoConsulta() throws Exception {
        long pacienteId = criar("/pacientes", PACIENTE_JSON);
        long medicoId = criar("/medicos", MEDICO_JSON);

        String consultaJson = """
                {"pacienteId":%d,"medicoId":%d,"dataHora":"%s","observacoes":"Primeira consulta"}
                """.formatted(pacienteId, medicoId, proximaSegunda(10));

        long consultaId = criar("/consultas", consultaJson);

        // mesmo médico, mesmo horário, outro paciente -> conflito
        long outroPaciente = criar("/pacientes", PACIENTE_JSON.replace("52998224725", "11144477735"));
        mvc.perform(post("/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson.replace("\"pacienteId\":" + pacienteId, "\"pacienteId\":" + outroPaciente)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("O médico já tem uma consulta neste horário"));

        // não pode excluir paciente com consulta agendada
        mvc.perform(delete("/pacientes/" + pacienteId)).andExpect(status().isUnprocessableEntity());

        // cancela
        mvc.perform(patch("/consultas/" + consultaId + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Paciente viajou\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    @DisplayName("Médico inativado não aparece na busca por especialidade")
    void inativarMedico() throws Exception {
        long medicoId = criar("/medicos", MEDICO_JSON);

        mvc.perform(delete("/medicos/" + medicoId)).andExpect(status().isNoContent());

        mvc.perform(get("/medicos?especialidade=CARDIOLOGIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
