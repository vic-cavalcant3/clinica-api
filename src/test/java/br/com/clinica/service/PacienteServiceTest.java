package br.com.clinica.service;

import br.com.clinica.dto.PacienteRequest;
import br.com.clinica.exception.RecursoNaoEncontradoException;
import br.com.clinica.exception.RegraNegocioException;
import br.com.clinica.model.Paciente;
import br.com.clinica.model.StatusConsulta;
import br.com.clinica.repository.ConsultaRepository;
import br.com.clinica.repository.PacienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ConsultaRepository consultaRepository;

    @InjectMocks
    private PacienteService service;

    private PacienteRequest request(String cpf) {
        return new PacienteRequest("  Ana Souza ", cpf, "ANA@Email.com", "11999999999", LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("Deve cadastrar paciente limpando CPF e padronizando e-mail")
    void deveCadastrarPaciente() {
        when(pacienteRepository.findByCpf("52998224725")).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        Paciente salvo = service.cadastrar(request("529.982.247-25"));

        assertThat(salvo.getCpf()).isEqualTo("52998224725");
        assertThat(salvo.getEmail()).isEqualTo("ana@email.com");
        assertThat(salvo.getNome()).isEqualTo("Ana Souza");
    }

    @Test
    @DisplayName("Não deve cadastrar CPF duplicado")
    void naoDeveCadastrarCpfDuplicado() {
        Paciente existente = new Paciente();
        existente.setId(1L);
        when(pacienteRepository.findByCpf("52998224725")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.cadastrar(request("52998224725")))
                .isInstanceOf(RegraNegocioException.class);
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Atualizar mantendo o próprio CPF não é considerado duplicado")
    void atualizarMantendoProprioCpf() {
        Paciente paciente = new Paciente();
        paciente.setId(1L);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(pacienteRepository.findByCpf("52998224725")).thenReturn(Optional.of(paciente));
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        Paciente atualizado = service.atualizar(1L, request("52998224725"));

        assertThat(atualizado.getNome()).isEqualTo("Ana Souza");
    }

    @Test
    @DisplayName("Deve lançar erro ao buscar paciente inexistente")
    void buscarInexistente() {
        when(pacienteRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(42L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Não deve excluir paciente com consulta agendada")
    void naoDeveExcluirComConsultaAgendada() {
        Paciente paciente = new Paciente();
        paciente.setId(1L);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(consultaRepository.existsByPacienteIdAndStatus(1L, StatusConsulta.AGENDADA)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(1L))
                .isInstanceOf(RegraNegocioException.class);
        verify(pacienteRepository, never()).delete(any());
    }
}
