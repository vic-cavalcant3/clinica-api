package br.com.clinica.service;

import br.com.clinica.dto.ConsultaRequest;
import br.com.clinica.exception.RegraNegocioException;
import br.com.clinica.model.*;
import br.com.clinica.repository.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaRepository consultaRepository;
    @Mock
    private PacienteService pacienteService;
    @Mock
    private MedicoService medicoService;

    @InjectMocks
    private ConsultaService service;

    private Paciente paciente;
    private Medico medico;

    @BeforeEach
    void setUp() {
        paciente = new Paciente("Ana Souza", "52998224725", "ana@email.com", "11999999999", LocalDate.of(2000, 1, 1));
        paciente.setId(1L);
        medico = new Medico("Dr. Carlos", "123456-SP", Especialidade.CARDIOLOGIA);
        medico.setId(1L);
    }

    /** Uma segunda-feira daqui a 1-2 semanas: sempre futura e dentro do horário da clínica. */
    private LocalDateTime proximaSegunda(int hora) {
        return LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(hora, 0);
    }

    private void mockPacienteEMedico() {
        when(pacienteService.buscarPorId(1L)).thenReturn(paciente);
        when(medicoService.buscarPorId(1L)).thenReturn(medico);
    }

    @Test
    @DisplayName("Deve agendar consulta válida")
    void deveAgendarConsultaValida() {
        mockPacienteEMedico();
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));

        Consulta consulta = service.agendar(new ConsultaRequest(1L, 1L, proximaSegunda(10), "Retorno"));

        assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
        assertThat(consulta.getMedico()).isEqualTo(medico);
        verify(consultaRepository).save(any(Consulta.class));
    }

    @Test
    @DisplayName("Não deve agendar no domingo")
    void naoDeveAgendarNoDomingo() {
        mockPacienteEMedico();
        LocalDateTime domingo = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atTime(10, 0);

        assertThatThrownBy(() -> service.agendar(new ConsultaRequest(1L, 1L, domingo, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("domingo");
        verify(consultaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não deve agendar fora do horário de funcionamento")
    void naoDeveAgendarForaDoHorario() {
        mockPacienteEMedico();

        assertThatThrownBy(() -> service.agendar(new ConsultaRequest(1L, 1L, proximaSegunda(20), null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("07:00 e 18:00");
    }

    @Test
    @DisplayName("Não deve agendar em horário quebrado (ex.: 10:30)")
    void naoDeveAgendarHorarioQuebrado() {
        mockPacienteEMedico();

        assertThatThrownBy(() -> service.agendar(
                new ConsultaRequest(1L, 1L, proximaSegunda(10).plusMinutes(30), null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("hora cheia");
    }

    @Test
    @DisplayName("Não deve agendar com médico inativo")
    void naoDeveAgendarComMedicoInativo() {
        medico.setAtivo(false);
        mockPacienteEMedico();

        assertThatThrownBy(() -> service.agendar(new ConsultaRequest(1L, 1L, proximaSegunda(10), null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("Não deve agendar se o médico já tem consulta no horário")
    void naoDeveAgendarMedicoOcupado() {
        mockPacienteEMedico();
        Consulta existente = new Consulta(paciente, medico, proximaSegunda(10), null);
        existente.setId(99L);
        when(consultaRepository.findByMedicoIdAndDataHoraAndStatus(1L, proximaSegunda(10), StatusConsulta.AGENDADA))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.agendar(new ConsultaRequest(1L, 1L, proximaSegunda(10), null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("médico já tem uma consulta");
    }

    @Test
    @DisplayName("Não deve agendar duas consultas pro mesmo paciente no mesmo dia")
    void naoDeveAgendarPacienteDuasVezesNoDia() {
        mockPacienteEMedico();
        Consulta existente = new Consulta(paciente, medico, proximaSegunda(8), null);
        existente.setId(99L);
        when(consultaRepository.findByPacienteIdAndDataHoraBetweenAndStatus(
                eq(1L), any(), any(), eq(StatusConsulta.AGENDADA)))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.agendar(new ConsultaRequest(1L, 1L, proximaSegunda(15), null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("paciente já tem uma consulta");
    }

    @Test
    @DisplayName("Remarcar para o mesmo horário não conflita com a própria consulta")
    void remarcarNaoConflitaComElaMesma() {
        mockPacienteEMedico();
        Consulta consulta = new Consulta(paciente, medico, proximaSegunda(10), null);
        consulta.setId(5L);
        when(consultaRepository.findById(5L)).thenReturn(Optional.of(consulta));
        when(consultaRepository.findByMedicoIdAndDataHoraAndStatus(1L, proximaSegunda(10), StatusConsulta.AGENDADA))
                .thenReturn(List.of(consulta));
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));

        Consulta resultado = service.remarcar(5L, new ConsultaRequest(1L, 1L, proximaSegunda(10), "Nova obs"));

        assertThat(resultado.getObservacoes()).isEqualTo("Nova obs");
    }

    @Test
    @DisplayName("Deve cancelar consulta com motivo e antecedência")
    void deveCancelarConsulta() {
        Consulta consulta = new Consulta(paciente, medico, LocalDateTime.now().plusDays(3), null);
        consulta.setId(5L);
        when(consultaRepository.findById(5L)).thenReturn(Optional.of(consulta));
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));

        Consulta resultado = service.cancelar(5L, "Paciente viajou");

        assertThat(resultado.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
        assertThat(resultado.getMotivoCancelamento()).isEqualTo("Paciente viajou");
    }

    @Test
    @DisplayName("Não deve cancelar com menos de 24h de antecedência")
    void naoDeveCancelarEmCimaDaHora() {
        Consulta consulta = new Consulta(paciente, medico, LocalDateTime.now().plusHours(5), null);
        consulta.setId(5L);
        when(consultaRepository.findById(5L)).thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> service.cancelar(5L, "Imprevisto"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("24h");
    }

    @Test
    @DisplayName("Não deve cancelar consulta já cancelada")
    void naoDeveCancelarDuasVezes() {
        Consulta consulta = new Consulta(paciente, medico, LocalDateTime.now().plusDays(3), null);
        consulta.setId(5L);
        consulta.setStatus(StatusConsulta.CANCELADA);
        when(consultaRepository.findById(5L)).thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> service.cancelar(5L, "De novo"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CANCELADA");
    }

    @Test
    @DisplayName("Não deve finalizar consulta que ainda não aconteceu")
    void naoDeveFinalizarConsultaFutura() {
        Consulta consulta = new Consulta(paciente, medico, LocalDateTime.now().plusDays(1), null);
        consulta.setId(5L);
        when(consultaRepository.findById(5L)).thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> service.marcarComoRealizada(5L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ainda não aconteceu");
    }
}
