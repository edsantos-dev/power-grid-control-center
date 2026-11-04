package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.FalhaEquipamentoRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FalhaEquipamentoService {
    private final AtivoEmFalhaValidator bloqueios;
    private final EventoOperacionalRepository eventos;
    private final FalhaEquipamentoRepository falhas;
    private final UsuarioRepository usuarios;
    private final EntityManager em;
    private final Clock clock;

    public FalhaEquipamentoService(AtivoEmFalhaValidator bloqueios, EventoOperacionalRepository eventos,
            FalhaEquipamentoRepository falhas, UsuarioRepository usuarios, EntityManager em, Clock clock) {
        this.bloqueios = bloqueios; this.eventos = eventos; this.falhas = falhas;
        this.usuarios = usuarios; this.em = em; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public FalhaEquipamento registrar(Long ativoId, Long responsavelUsuarioId, String descricao, Criticidade criticidade) {
        if (ativoId == null || ativoId <= 0 || responsavelUsuarioId == null || responsavelUsuarioId <= 0
                || descricao == null || descricao.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ativo, responsável e descrição não vazia.");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var registrador = usuarios.findByEmail(NormalizadorEmail.normalizar(auth.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (registrador.getPerfil() != PerfilAcesso.GERENTE_OPERACOES && registrador.getPerfil() != PerfilAcesso.OPERADOR)
            throw new AccessDeniedException("Somente gerente e operador podem registrar falhas.");
        bloqueios.bloquear(ativoId);
        var ativo = em.find(AtivoRede.class, ativoId);
        if (eventos.existeFalhaNaoConcluida(ativoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe falha não concluída para este ativo.");
        }
        var responsavel = usuarios.findById(responsavelUsuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado."));
        var falha = new FalhaEquipamento();
        falha.setEstadoAnterior(ativo.getEstadoOperacional());
        falha.setRegistradoEm(clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        var evento = new EventoOperacional(); evento.setAtivo(ativo);
        evento.setCategoria(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        evento.setSituacao(SituacaoEventoOperacional.ABERTO.name()); evento.setDescricao(descricao.trim());
        evento.setCriticidade(criticidade == null ? null : criticidade.name());
        evento.setRegistradorUsuario(registrador); evento.setResponsavelUsuario(responsavel);
        eventos.saveAndFlush(evento);
        falha.setEventoOperacional(evento);
        ativo.setEstadoOperacional(EstadoOperacional.FALHA);
        return falhas.saveAndFlush(falha);
    }
}
