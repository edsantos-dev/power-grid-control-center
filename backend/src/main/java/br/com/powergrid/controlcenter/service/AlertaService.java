package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import br.com.powergrid.controlcenter.dto.AlertaRequestDTO;
import br.com.powergrid.controlcenter.dto.AlertaResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.AlertaRepository;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaService {
    private final AlertaRepository repository;
    private final EntityManager entityManager;
    private final HistoricoAlertaRepository historicos;
    private final Clock clock;

    public AlertaService(AlertaRepository repository, EntityManager entityManager,
            HistoricoAlertaRepository historicos, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.historicos = historicos;
        this.clock = clock;
    }

    @Transactional
    public AlertaResponseDTO criar(AlertaRequestDTO request) {
        AtivoRede ativo = validarEscopo(request);
        Alerta alerta = new Alerta();
        alerta.setTipo(request.tipo());
        alerta.setCriticidade(request.criticidade());
        alerta.setEscopo(request.escopo());
        alerta.setAtivo(ativo);
        alerta.setGeradoEm(clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        alerta.setSituacao(SituacaoAlerta.ABERTO);
        return toResponse(repository.save(alerta), List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AlertaResponseDTO> listarAtivos() {
        var alertas = repository.buscarAtivos();
        var ids = alertas.stream().map(Alerta::getId).toList();
        var porAlerta = historicos.buscarPorAlertas(ids).stream().collect(Collectors.groupingBy(h -> h.getAlerta().getId()));
        return alertas.stream().map(a -> toResponse(a, porAlerta.getOrDefault(a.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AlertaResponseDTO obterPorId(Long id) {
        var estado = repository.consultarEstado(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta não encontrado."));
        if (estado.getSituacao() == null) throw conflitoLegado();
        try { SituacaoAlerta.valueOf(estado.getSituacao()); }
        catch (IllegalArgumentException ex) { throw conflitoLegado(); }
        var alerta = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta não encontrado."));
        return toResponse(alerta, historicos.buscarPorAlertaOrdenado(id, 2, 0));
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

    private AlertaResponseDTO toResponse(Alerta alerta, List<HistoricoAlerta> auditoria) {
        var reconhecimento = auditoria.stream().filter(h -> h.getSituacaoNova() == SituacaoAlerta.RECONHECIDO).findFirst().orElse(null);
        var resolucao = auditoria.stream().filter(h -> h.getSituacaoNova() == SituacaoAlerta.RESOLVIDO).findFirst().orElse(null);
        return new AlertaResponseDTO(alerta.getId(), alerta.getTipo(), alerta.getCriticidade(),
                alerta.getEscopo(), alerta.getSituacao(), utc(alerta.getGeradoEm()),
                alerta.getAtivo() == null ? null : alerta.getAtivo().getId(),
                reconhecimento == null ? null : utc(reconhecimento.getAlteradoEm()),
                reconhecimento == null ? null : reconhecimento.getUsuario().getId(),
                resolucao == null ? null : utc(resolucao.getAlteradoEm()),
                resolucao == null ? null : resolucao.getUsuario().getId());
    }

    private OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private ResponseStatusException conflitoLegado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Alerta com situação legada inválida exige regularização.");
    }
}
