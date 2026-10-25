package br.com.powergrid.controlcenter.service;

import java.util.List;
import br.com.powergrid.controlcenter.dto.EventoOperacionalRequestDTO;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventoOperacionalService {
    private final EventoOperacionalRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    public EventoOperacionalService(EventoOperacionalRepository repository,
            UsuarioRepository usuarioRepository, EntityManager entityManager) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public EventoOperacionalResponseDTO criar(EventoOperacionalRequestDTO request) {
        Usuario registrador = usuarioAutenticado();
        AtivoRede ativo = entityManager.find(AtivoRede.class, request.ativoId());
        if (ativo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
        Usuario responsavel = usuarioRepository.findById(request.responsavelUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado."));
        EventoOperacional evento = new EventoOperacional();
        evento.setAtivo(ativo);
        evento.setCategoria(request.categoria());
        evento.setDescricao(request.descricao());
        evento.setCriticidade(request.criticidade());
        evento.setRegistradorUsuario(registrador);
        evento.setResponsavelUsuario(responsavel);
        evento.setInicioPrevisto(request.inicioPrevisto());
        evento.setTerminoPrevisto(request.terminoPrevisto());
        evento.setSituacao("ABERTO");
        return toResponse(repository.save(evento));
    }

    @Transactional(readOnly = true)
    public List<EventoOperacionalResponseDTO> listar() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventoOperacionalResponseDTO buscarPorId(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado.")));
    }

    private Usuario usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return usuarioRepository.findByEmail(NormalizadorEmail.normalizar(authentication.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private EventoOperacionalResponseDTO toResponse(EventoOperacional evento) {
        return new EventoOperacionalResponseDTO(evento.getId(), evento.getAtivo().getId(),
                evento.getCategoria(), evento.getDescricao(), evento.getCriticidade(), evento.getSituacao(),
                evento.getRegistradorUsuario().getId(), evento.getResponsavelUsuario().getId(),
                evento.getInicioPrevisto(), evento.getTerminoPrevisto(),
                evento.getInicioEfetivo(), evento.getTerminoEfetivo());
    }
}
