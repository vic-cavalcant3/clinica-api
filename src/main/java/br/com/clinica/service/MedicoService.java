package br.com.clinica.service;

import br.com.clinica.dto.MedicoRequest;
import br.com.clinica.exception.RecursoNaoEncontradoException;
import br.com.clinica.exception.RegraNegocioException;
import br.com.clinica.model.Especialidade;
import br.com.clinica.model.Medico;
import br.com.clinica.model.StatusConsulta;
import br.com.clinica.repository.ConsultaRepository;
import br.com.clinica.repository.MedicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final ConsultaRepository consultaRepository;

    public MedicoService(MedicoRepository medicoRepository, ConsultaRepository consultaRepository) {
        this.medicoRepository = medicoRepository;
        this.consultaRepository = consultaRepository;
    }

    public List<Medico> listar(Especialidade especialidade) {
        if (especialidade != null) {
            return medicoRepository.findByEspecialidadeAndAtivoTrue(especialidade);
        }
        return medicoRepository.findAll();
    }

    public Medico buscarPorId(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: id " + id));
    }

    @Transactional
    public Medico cadastrar(MedicoRequest dados) {
        medicoRepository.findByCrm(dados.crm()).ifPresent(m -> {
            throw new RegraNegocioException("Já existe médico cadastrado com este CRM");
        });
        return medicoRepository.save(new Medico(dados.nome().trim(), dados.crm(), dados.especialidade()));
    }

    @Transactional
    public Medico atualizar(Long id, MedicoRequest dados) {
        Medico medico = buscarPorId(id);
        medicoRepository.findByCrm(dados.crm()).ifPresent(outro -> {
            if (!outro.getId().equals(id)) {
                throw new RegraNegocioException("Já existe outro médico cadastrado com este CRM");
            }
        });
        medico.setNome(dados.nome().trim());
        medico.setCrm(dados.crm());
        medico.setEspecialidade(dados.especialidade());
        return medicoRepository.save(medico);
    }

    /**
     * Médico não é apagado do banco (pra manter o histórico de consultas):
     * ele só fica inativo e deixa de receber agendamentos.
     */
    @Transactional
    public void inativar(Long id) {
        Medico medico = buscarPorId(id);
        if (consultaRepository.existsByMedicoIdAndStatus(id, StatusConsulta.AGENDADA)) {
            throw new RegraNegocioException("Não é possível inativar médico com consultas agendadas");
        }
        medico.setAtivo(false);
        medicoRepository.save(medico);
    }
}
