package br.com.powergrid.controlcenter.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import br.com.powergrid.controlcenter.dto.AlertaRequestDTO;
import br.com.powergrid.controlcenter.dto.AlertaResponseDTO;
import br.com.powergrid.controlcenter.model.Alerta;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import br.com.powergrid.controlcenter.repository.AlertaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaService {
    private final AlertaRepository repository;
    private final EntityManager entityManager;

    public AlertaService(AlertaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AlertaResponseDTO criar(AlertaRequestDTO request) {
        AtivoRede ativo = validarEscopo(request);
        Alerta alerta = new Alerta();
        alerta.setTipo(request.tipo());
        alerta.setCriticidade(request.criticidade());
        alerta.setEscopo(request.escopo());
        alerta.setAtivo(ativo);
        alerta.setGeradoEm(OffsetDateTime.now(ZoneOffset.UTC));
        alerta.setSituacao(SituacaoAlerta.ABERTO);
        return toResponse(repository.save(alerta));
    }

    @Transactional(readOnly = true)
    public List<AlertaResponseDTO> listarAtivos() {
        return repository.buscarAtivos().stream().map(this::toResponse).toList();
    }

    private AtivoRede validarEscopo(AlertaRequestDTO request) {
        if (request.escopo() == EscopoAlerta.ATIVO) {
            if (request.ativoId() == null || request.ativoId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escopo ATIVO exige ativoId positivo.");
            }
            AtivoRede ativo = entityManager.find(AtivoRede.class, request.ativoId());
            if (ativo == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
            }
            return ativo;
        }
        if (request.escopo() != EscopoAlerta.REDE_MONITORADA || request.ativoId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escopo REDE_MONITORADA exige ativoId nulo.");
        }
        return null;
    }

    private AlertaResponseDTO toResponse(Alerta alerta) {
        return new AlertaResponseDTO(alerta.getId(), alerta.getTipo(), alerta.getCriticidade(),
                alerta.getEscopo(), alerta.getSituacao(), alerta.getGeradoEm(),
                alerta.getAtivo() == null ? null : alerta.getAtivo().getId());
    }
}
