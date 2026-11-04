package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.FalhaEquipamentoRequestDTO;
import br.com.powergrid.controlcenter.dto.FalhaEquipamentoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.mapper.EventoOperacionalMapper;
import br.com.powergrid.controlcenter.repository.FalhaEquipamentoConsultaRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.annotation.Isolation;
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
    private final FalhaEquipamentoConsultaRepository consultas;

    public FalhaEquipamentoService(AtivoEmFalhaValidator bloqueios, EventoOperacionalRepository eventos,
            FalhaEquipamentoRepository falhas, UsuarioRepository usuarios, EntityManager em, Clock clock,
            FalhaEquipamentoConsultaRepository consultas) {
        this.consultas = consultas;
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
    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
    public FalhaEquipamentoResponseDTO criar(FalhaEquipamentoRequestDTO request) {
        return resposta(registrar(request.ativoId(), request.responsavelUsuarioId(), request.descricao(), request.criticidade()));
    }

    @Transactional(readOnly = true)
    public FalhaEquipamentoResponseDTO obterPorId(Long id) {
        var falha = falhas.findDetalhadaByIdAndEventoOperacionalCategoria(id, EventoOperacional.Categoria.FALHA_EQUIPAMENTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Falha de equipamento não encontrada."));
        return resposta(falha);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponseDTO<FalhaEquipamentoResponseDTO> listar(MultiValueMap<String, String> params) {
        var permitidos = Set.of("page", "size", "ativoId", "situacao", "dataInicio", "dataFim");
        for (String nome : params.keySet()) {
            if (!permitidos.contains(nome)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro não permitido: " + nome);
            ParametrosConsultaMedicao.parametro(params, nome);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "registradoEm");
        String situacao = ParametrosConsultaMedicao.parametro(params, "situacao");
        if (situacao != null) {
            try { SituacaoEventoOperacional.valueOf(situacao); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação de falha inválida."); }
        }
        Long ativoId = null;
        String id = ParametrosConsultaMedicao.parametro(params, "ativoId");
        if (id != null) {
            try {
                if (!id.matches("[0-9]+")) throw new NumberFormatException();
                ativoId = Long.parseLong(id); if (ativoId <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ativoId deve ser um inteiro positivo válido."); }
        }
        return PageResponseDTO.from(consultas.buscar(ativoId, situacao, consulta.inicio(), consulta.fim(), consulta.pageable()).map(this::resposta));
    }

    private FalhaEquipamentoResponseDTO resposta(FalhaEquipamento falha) {
        return new FalhaEquipamentoResponseDTO(falha.getId(), EventoOperacionalMapper.toResponse(falha.getEventoOperacional(), falha.getEstadoAposReparo()),
                falha.getEstadoAnterior(), falha.getRegistradoEm().withOffsetSameInstant(ZoneOffset.UTC), falha.getEstadoAposReparo(),
                falha.getEventoOperacional().getAtivo().getEstadoOperacional());
    }

}
