package br.com.clinica.repository;

import br.com.clinica.model.Consulta;
import br.com.clinica.model.StatusConsulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    List<Consulta> findByMedicoIdAndDataHoraAndStatus(Long medicoId, LocalDateTime dataHora, StatusConsulta status);

    List<Consulta> findByPacienteIdAndDataHoraBetweenAndStatus(Long pacienteId, LocalDateTime inicio,
                                                              LocalDateTime fim, StatusConsulta status);

    boolean existsByPacienteIdAndStatus(Long pacienteId, StatusConsulta status);

    boolean existsByMedicoIdAndStatus(Long medicoId, StatusConsulta status);

    List<Consulta> findByPacienteId(Long pacienteId);

    List<Consulta> findByMedicoId(Long medicoId);
}
