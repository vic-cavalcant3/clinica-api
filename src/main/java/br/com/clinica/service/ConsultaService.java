package br.com.clinica.service;

import br.com.clinica.dto.ConsultaRequest;
import br.com.clinica.exception.RecursoNaoEncontradoException;
import br.com.clinica.exception.RegraNegocioException;
import br.com.clinica.model.Consulta;
import br.com.clinica.model.Medico;
import br.com.clinica.model.Paciente;
import br.com.clinica.model.StatusConsulta;
import br.com.clinica.repository.ConsultaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ConsultaService {

    static final LocalTime ABERTURA = LocalTime.of(7, 0);
    static final LocalTime ULTIMO_HORARIO = LocalTime.of(18, 0);
    static final int ANTECEDENCIA_MINIMA_MINUTOS = 30;
    static final int ANTECEDENCIA_CANCELAMENTO_HORAS = 24;

    private final ConsultaRepository consultaRepository;
    private final PacienteService pacienteService;
    private final MedicoService medicoService;

    public ConsultaService(ConsultaRepository consultaRepository, PacienteService pacienteService,
                           MedicoService medicoService) {
        this.consultaRepository = consultaRepository;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
    }

    public List<Consulta> listar(Long pacienteId, Long medicoId) {
        if (pacienteId != null) {
            return consultaRepository.findByPacienteId(pacienteId);
        }
        if (medicoId != null) {
            return consultaRepository.findByMedicoId(medicoId);
        }
        return consultaRepository.findAll();
    }

    public Consulta buscarPorId(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta não encontrada: id " + id));
    }

    @Transactional
    public Consulta agendar(ConsultaRequest dados) {
        Paciente paciente = pacienteService.buscarPorId(dados.pacienteId());
        Medico medico = medicoService.buscarPorId(dados.medicoId());

        validarAgendamento(null, paciente, medico, dados.dataHora());

        Consulta consulta = new Consulta(paciente, medico, dados.dataHora(), dados.observacoes());
        return consultaRepository.save(consulta);
    }

    @Transactional
    public Consulta remarcar(Long id, ConsultaRequest dados) {
        Consulta consulta = buscarPorId(id);
        exigirAgendada(consulta, "remarcar");

        Paciente paciente = pacienteService.buscarPorId(dados.pacienteId());
        Medico medico = medicoService.buscarPorId(dados.medicoId());

        validarAgendamento(id, paciente, medico, dados.dataHora());

        consulta.setPaciente(paciente);
        consulta.setMedico(medico);
        consulta.setDataHora(dados.dataHora());
        consulta.setObservacoes(dados.observacoes());
        return consultaRepository.save(consulta);
    }

    @Transactional
    public Consulta cancelar(Long id, String motivo) {
        Consulta consulta = buscarPorId(id);
        exigirAgendada(consulta, "cancelar");

        if (LocalDateTime.now().plusHours(ANTECEDENCIA_CANCELAMENTO_HORAS).isAfter(consulta.getDataHora())) {
            throw new RegraNegocioException(
                    "Cancelamento só é permitido com " + ANTECEDENCIA_CANCELAMENTO_HORAS + "h de antecedência");
        }

        consulta.setStatus(StatusConsulta.CANCELADA);
        consulta.setMotivoCancelamento(motivo);
        return consultaRepository.save(consulta);
    }

    @Transactional
    public Consulta marcarComoRealizada(Long id) {
        Consulta consulta = buscarPorId(id);
        exigirAgendada(consulta, "finalizar");

        if (consulta.getDataHora().isAfter(LocalDateTime.now())) {
            throw new RegraNegocioException("Não é possível finalizar uma consulta que ainda não aconteceu");
        }
        consulta.setStatus(StatusConsulta.REALIZADA);
        return consultaRepository.save(consulta);
    }

    // ---------------- regras de negócio ----------------

    void validarAgendamento(Long idConsultaAtual, Paciente paciente, Medico medico, LocalDateTime dataHora) {
        validarHorarioFuncionamento(dataHora);
        validarAntecedencia(dataHora);
        validarMedicoAtivo(medico);
        validarMedicoLivre(idConsultaAtual, medico, dataHora);
        validarPacienteSemOutraConsultaNoDia(idConsultaAtual, paciente, dataHora);
    }

    private void validarHorarioFuncionamento(LocalDateTime dataHora) {
        if (dataHora.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new RegraNegocioException("A clínica não funciona aos domingos");
        }
        LocalTime hora = dataHora.toLocalTime();
        if (hora.isBefore(ABERTURA) || hora.isAfter(ULTIMO_HORARIO)) {
            throw new RegraNegocioException("Consultas só podem ser marcadas entre 07:00 e 18:00");
        }
        if (dataHora.getMinute() != 0 || dataHora.getSecond() != 0) {
            throw new RegraNegocioException("Consultas devem começar em hora cheia (ex.: 09:00, 14:00)");
        }
    }

    private void validarAntecedencia(LocalDateTime dataHora) {
        if (dataHora.isBefore(LocalDateTime.now().plusMinutes(ANTECEDENCIA_MINIMA_MINUTOS))) {
            throw new RegraNegocioException(
                    "A consulta deve ser marcada com pelo menos " + ANTECEDENCIA_MINIMA_MINUTOS + " minutos de antecedência");
        }
    }

    private void validarMedicoAtivo(Medico medico) {
        if (!medico.isAtivo()) {
            throw new RegraNegocioException("Médico inativo não pode receber consultas");
        }
    }

    private void validarMedicoLivre(Long idConsultaAtual, Medico medico, LocalDateTime dataHora) {
        boolean ocupado = consultaRepository
                .findByMedicoIdAndDataHoraAndStatus(medico.getId(), dataHora, StatusConsulta.AGENDADA)
                .stream()
                .anyMatch(c -> !c.getId().equals(idConsultaAtual));
        if (ocupado) {
            throw new RegraNegocioException("O médico já tem uma consulta neste horário");
        }
    }

    private void validarPacienteSemOutraConsultaNoDia(Long idConsultaAtual, Paciente paciente, LocalDateTime dataHora) {
        LocalDateTime inicioDia = dataHora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = dataHora.toLocalDate().atTime(LocalTime.MAX);
        boolean jaTem = consultaRepository
                .findByPacienteIdAndDataHoraBetweenAndStatus(paciente.getId(), inicioDia, fimDia, StatusConsulta.AGENDADA)
                .stream()
                .anyMatch(c -> !c.getId().equals(idConsultaAtual));
        if (jaTem) {
            throw new RegraNegocioException("O paciente já tem uma consulta agendada neste dia");
        }
    }

    private void exigirAgendada(Consulta consulta, String acao) {
        if (consulta.getStatus() != StatusConsulta.AGENDADA) {
            throw new RegraNegocioException(
                    "Não é possível " + acao + " uma consulta com status " + consulta.getStatus());
        }
    }
}
