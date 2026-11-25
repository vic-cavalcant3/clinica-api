package br.com.clinica.service;

import br.com.clinica.dto.PacienteRequest;
import br.com.clinica.exception.RecursoNaoEncontradoException;
import br.com.clinica.exception.RegraNegocioException;
import br.com.clinica.model.Paciente;
import br.com.clinica.model.StatusConsulta;
import br.com.clinica.repository.ConsultaRepository;
import br.com.clinica.repository.PacienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;

    public PacienteService(PacienteRepository pacienteRepository, ConsultaRepository consultaRepository) {
        this.pacienteRepository = pacienteRepository;
        this.consultaRepository = consultaRepository;
    }

    public List<Paciente> listar(String nome) {
        if (nome != null && !nome.isBlank()) {
            return pacienteRepository.findByNomeContainingIgnoreCase(nome);
        }
        return pacienteRepository.findAll();
    }

    public Paciente buscarPorId(Long id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado: id " + id));
    }

    @Transactional
    public Paciente cadastrar(PacienteRequest dados) {
        String cpf = somenteNumeros(dados.cpf());
        pacienteRepository.findByCpf(cpf).ifPresent(p -> {
            throw new RegraNegocioException("Já existe paciente cadastrado com este CPF");
        });

        Paciente paciente = new Paciente(dados.nome().trim(), cpf, dados.email().trim().toLowerCase(),
                dados.telefone(), dados.dataNascimento());
        return pacienteRepository.save(paciente);
    }

    @Transactional
    public Paciente atualizar(Long id, PacienteRequest dados) {
        Paciente paciente = buscarPorId(id);
        String cpf = somenteNumeros(dados.cpf());

        pacienteRepository.findByCpf(cpf).ifPresent(outro -> {
            if (!outro.getId().equals(id)) {
                throw new RegraNegocioException("Já existe outro paciente cadastrado com este CPF");
            }
        });

        paciente.setNome(dados.nome().trim());
        paciente.setCpf(cpf);
        paciente.setEmail(dados.email().trim().toLowerCase());
        paciente.setTelefone(dados.telefone());
        paciente.setDataNascimento(dados.dataNascimento());
        return pacienteRepository.save(paciente);
    }

    @Transactional
    public void excluir(Long id) {
        Paciente paciente = buscarPorId(id);
        if (consultaRepository.existsByPacienteIdAndStatus(id, StatusConsulta.AGENDADA)) {
            throw new RegraNegocioException("Não é possível excluir paciente com consultas agendadas");
        }
        consultaRepository.deleteAll(consultaRepository.findByPacienteId(id));
        pacienteRepository.delete(paciente);
    }

    private String somenteNumeros(String valor) {
        return valor.replaceAll("\\D", "");
    }
}
