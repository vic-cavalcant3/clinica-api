package br.com.clinica.controller;

import br.com.clinica.dto.CancelamentoRequest;
import br.com.clinica.dto.ConsultaRequest;
import br.com.clinica.model.Consulta;
import br.com.clinica.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/consultas")
public class ConsultaController {

    private final ConsultaService service;

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Consulta> listar(@RequestParam(required = false) Long pacienteId,
                                 @RequestParam(required = false) Long medicoId) {
        return service.listar(pacienteId, medicoId);
    }

    @GetMapping("/{id}")
    public Consulta buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Consulta agendar(@RequestBody @Valid ConsultaRequest dados) {
        return service.agendar(dados);
    }

    @PutMapping("/{id}")
    public Consulta remarcar(@PathVariable Long id, @RequestBody @Valid ConsultaRequest dados) {
        return service.remarcar(id, dados);
    }

    @PatchMapping("/{id}/cancelar")
    public Consulta cancelar(@PathVariable Long id, @RequestBody @Valid CancelamentoRequest dados) {
        return service.cancelar(id, dados.motivo());
    }

    @PatchMapping("/{id}/realizar")
    public Consulta realizar(@PathVariable Long id) {
        return service.marcarComoRealizada(id);
    }
}
