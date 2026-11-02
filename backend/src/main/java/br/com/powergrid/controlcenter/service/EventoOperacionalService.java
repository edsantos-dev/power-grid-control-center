package br.com.powergrid.controlcenter.service;

import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.util.MultiValueMap;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.model.HistoricoEventoOperacional;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import org.springframework.security.access.AccessDeniedException;
import br.com.powergrid.controlcenter.dto.EventoOperacionalRequestDTO;
import br.com.powergrid.controlcenter.dto.InterrupcaoRequestDTO;
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
    private final HistoricoEventoOperacionalRepository historicos;
    private final Clock clock;

    public EventoOperacionalService(EventoOperacionalRepository repository,
            UsuarioRepository usuarioRepository, EntityManager entityManager,
            HistoricoEventoOperacionalRepository historicos, Clock clock) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
        this.historicos = historicos;
        this.clock = clock;
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

    @Transactional
    public EventoOperacionalResponseDTO criarInterrupcao(InterrupcaoRequestDTO request) {
        Usuario registrador = usuarioAutenticado();
        if (request.inicioEfetivo() == null || request.inicioEfetivo().toInstant().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o início efetivo, que não pode estar no futuro.");
        }
        AtivoRede ativo = entityManager.find(AtivoRede.class, request.ativoId());
        if (ativo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
        Usuario responsavel = usuarioRepository.findById(request.responsavelUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado."));
        var evento = new EventoOperacional();
        evento.setAtivo(ativo);
        evento.setCategoria(EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO);
        evento.setDescricao(request.descricao());
        evento.setCriticidade(request.criticidade());
        evento.setRegistradorUsuario(registrador);
        evento.setResponsavelUsuario(responsavel);
        evento.setSituacao(SituacaoEventoOperacional.ABERTO.name());
        evento.setInicioEfetivo(request.inicioEfetivo().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        return toResponse(repository.save(evento));
    }

    @Transactional
    public Long transicionar(Long eventoId, SituacaoEventoOperacional situacaoEsperada,
            SituacaoEventoOperacional situacaoDestino, String comentario) {
        return transicionar(eventoId, situacaoEsperada, situacaoDestino, comentario, null);
    }

    @Transactional
    public Long transicionar(Long eventoId, SituacaoEventoOperacional situacaoEsperada,
            SituacaoEventoOperacional situacaoDestino, String comentario, OffsetDateTime terminoEfetivo) {
        Usuario usuario = usuarioAutenticado();
        EventoOperacional evento = repository.buscarPorIdParaTransicao(eventoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        boolean gerente = usuario.getPerfil() == PerfilAcesso.GERENTE_OPERACOES;
        boolean executor = usuario.getPerfil() == PerfilAcesso.OPERADOR
                || usuario.getPerfil() == PerfilAcesso.TECNICO_MANUTENCAO;
        if (!gerente && !(executor && usuario.getId().equals(evento.getResponsavelUsuario().getId()))) {
            throw new AccessDeniedException("Somente o responsável ou um gerente pode transicionar este evento.");
        }
        if (situacaoEsperada == null || situacaoDestino == null || comentario == null
                || comentario.isBlank() || comentario.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe situações e comentário válido de até 1.000 caracteres.");
        }
        if (!situacaoEsperada.name().equals(evento.getSituacao())) {
            throw conflito("A situação do evento diverge da situação esperada. Consulte o evento novamente.");
        }
        boolean iniciar = situacaoEsperada == SituacaoEventoOperacional.ABERTO
                && situacaoDestino == SituacaoEventoOperacional.EM_ANDAMENTO;
        boolean concluir = situacaoEsperada == SituacaoEventoOperacional.EM_ANDAMENTO
                && situacaoDestino == SituacaoEventoOperacional.CONCLUIDO;
        if (!iniciar && !concluir) throw conflito("Transição de situação não permitida.");
        if (evento.getTerminoEfetivo() != null) {
            throw conflito("Evento já possui término efetivo e exige regularização antes da transição.");
        }
        boolean interrupcao = evento.getCategoria() == EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO;
        if (terminoEfetivo != null && (!concluir || !interrupcao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Término físico informado é permitido somente na conclusão de interrupções.");
        }
        var instanteServidor = clock.instant();
        var agora = instanteServidor.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        if (iniciar && evento.getInicioEfetivo() == null) evento.setInicioEfetivo(agora);
        if (concluir && interrupcao) {
            if (terminoEfetivo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o término efetivo da interrupção.");
            }
            if (evento.getInicioEfetivo() == null) throw conflito("Interrupção sem início efetivo exige regularização.");
            if (terminoEfetivo.toInstant().isAfter(instanteServidor) || terminoEfetivo.isBefore(evento.getInicioEfetivo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Término efetivo não pode ser futuro nem anterior ao início efetivo.");
            }
            evento.setTerminoEfetivo(terminoEfetivo.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        } else if (concluir) {
            if (evento.getInicioEfetivo() == null || agora.isBefore(evento.getInicioEfetivo())) {
                throw conflito("Conclusão exige início efetivo existente e término maior ou igual ao início.");
            }
            evento.setTerminoEfetivo(agora);
        }
        evento.setSituacao(situacaoDestino.name());
        repository.flush();
        var historico = new HistoricoEventoOperacional();
        historico.setEvento(evento); historico.setUsuario(usuario);
        historico.setSituacaoAnterior(situacaoEsperada); historico.setSituacaoNova(situacaoDestino);
        historico.setAlteradoEm(agora); historico.setComentario(comentario);
        return historicos.saveAndFlush(historico).getId();
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
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

    @Transactional(readOnly = true)
    public EventoOperacionalResponseDTO obterInterrupcao(Long id) {
        return toResponse(repository.findByIdAndCategoria(id, EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interrupção não encontrada.")));
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<EventoOperacionalResponseDTO> listarInterrupcoes(MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) {
            if (!Set.of("page", "size", "sort", "ativoId", "situacao", "dataInicio", "dataFim").contains(nome)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro não suportado: " + nome);
            }
        }
        var consulta = ParametrosConsultaMedicao.from(params, "inicioEfetivo");
        String situacao = ParametrosConsultaMedicao.parametro(params, "situacao");
        if (situacao != null) {
            try { SituacaoEventoOperacional.valueOf(situacao); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação de interrupção inválida."); }
        }
        Long ativoId = filtroAtivoInterrupcao(params);
        return PageResponseDTO.from(repository.buscarInterrupcoes(ativoId, situacao, consulta.inicio(), consulta.fim(), consulta.pageable())
                .map(this::toResponse));
    }

    private Long filtroAtivoInterrupcao(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "ativoId");
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ativoId deve ser um inteiro positivo válido.");
        }
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
