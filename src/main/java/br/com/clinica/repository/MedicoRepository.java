package br.com.clinica.repository;

import br.com.clinica.model.Especialidade;
import br.com.clinica.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    Optional<Medico> findByCrm(String crm);

    List<Medico> findByEspecialidadeAndAtivoTrue(Especialidade especialidade);
}
