package br.com.powergrid.controlcenter.service;

import java.util.List;
import java.util.HashMap;
import br.com.powergrid.controlcenter.mapper.EventoOperacionalMapper;
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
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.FalhaEquipamentoRepository;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.model.FalhaEquipamento;
import jakarta.persistence.LockModeType;
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
    private final AtivoEmFalhaValidator bloqueios;
    private final FalhaEquipamentoRepository falhas;

    public EventoOperacionalService(EventoOperacionalRepository repository,
            UsuarioRepository usuarioRepository, EntityManager entityManager,
            HistoricoEventoOperacionalRepository historicos, Clock clock,
            AtivoEmFalhaValidator bloqueios, FalhaEquipamentoRepository falhas) {
        this.bloqueios = bloqueios; this.falhas = falhas;
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
        this.historicos = historicos;
        this.clock = clock;
    }

    @Transactional
    public EventoOperacionalResponseDTO criar(EventoOperacionalRequestDTO request) {
        Usuario registrador = usuarioAutenticado();
        if (request.categoria() == null || !Set.of(EventoOperacional.Categoria.MANOBRA,
                EventoOperacional.Categoria.DESLIGAMENTO_PROGRAMADO, EventoOperacional.Categoria.MANUTENCAO).contains(request.categoria())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não permitida no cadastro manual.");
        }
        if (request.categoria() == EventoOperacional.Categoria.MANOBRA) bloqueios.bloquear(request.ativoId());
        AtivoRede ativo = entityManager.find(AtivoRede.class, request.ativoId());
        if (ativo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
        if (request.categoria() == EventoOperacional.Categoria.MANOBRA) bloqueios.validarOperacao(ativo);
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
        return transicionar(eventoId, situacaoEsperada, situacaoDestino, comentario, terminoEfetivo, null);
    }

    @Transactional
    public Long transicionar(Long eventoId, SituacaoEventoOperacional situacaoEsperada,
            SituacaoEventoOperacional situacaoDestino, String comentario, OffsetDateTime terminoEfetivo,
            EstadoOperacional estadoAposReparo) {
        Usuario usuario = usuarioAutenticado();
        EventoOperacional evento = repository.buscarPorIdParaTransicao(eventoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        entityManager.refresh(evento, LockModeType.PESSIMISTIC_WRITE);
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
        boolean falha = evento.getCategoria() == EventoOperacional.Categoria.FALHA_EQUIPAMENTO;
        if (estadoAposReparo != null && (!concluir || !falha)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado após reparo é permitido somente na conclusão de falhas.");
        }
        if (iniciar && evento.getCategoria() == EventoOperacional.Categoria.MANOBRA) {
            bloqueios.bloquear(evento.getAtivo().getId());
            bloqueios.validarOperacao(evento.getAtivo());
        }
        boolean interrupcao = evento.getCategoria() == EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO;
        if (terminoEfetivo != null && (!concluir || !interrupcao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Término físico informado é permitido somente na conclusão de interrupções.");
        }
        var composicaoFalha = concluir && falha ? validarConclusaoFalha(evento, estadoAposReparo) : null;
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
        if (composicaoFalha != null) {
            composicaoFalha.setEstadoAposReparo(estadoAposReparo);
            evento.getAtivo().setEstadoOperacional(estadoAposReparo);
        }
        evento.setSituacao(situacaoDestino.name());
        repository.flush();
        var historico = new HistoricoEventoOperacional();
        historico.setEvento(evento); historico.setUsuario(usuario);
        historico.setSituacaoAnterior(situacaoEsperada); historico.setSituacaoNova(situacaoDestino);
        historico.setAlteradoEm(agora); historico.setComentario(comentario);
        return historicos.saveAndFlush(historico).getId();
    }

    private FalhaEquipamento validarConclusaoFalha(EventoOperacional evento, EstadoOperacional estadoAposReparo) {
        if (estadoAposReparo == null || estadoAposReparo == EstadoOperacional.FALHA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe OPERACIONAL ou MANUTENCAO como estado após reparo.");
        }
        bloqueios.bloquear(evento.getAtivo().getId());
        var falha = falhas.findById(evento.getId()).orElseThrow(() -> conflito("Falha sem composição exige regularização."));
        if (falha.getEstadoAposReparo() != null) throw conflito("Falha já possui estado após reparo e exige regularização.");
        if (evento.getAtivo().getEstadoOperacional() != EstadoOperacional.FALHA) {
            throw conflito("Estado do ativo diverge da falha em atendimento. Regularize o registro.");
        }
        if (repository.existeOutraFalhaNaoConcluida(evento.getAtivo().getId(), evento.getId())) {
            throw conflito("Outra falha não concluída impede a liberação do equipamento.");
        }
        return falha;
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }

    @Transactional(readOnly = true)
    public List<EventoOperacionalResponseDTO> listar() {
        var registros = repository.findAll();
        var ids = registros.stream().filter(e -> e.getCategoria() == EventoOperacional.Categoria.FALHA_EQUIPAMENTO)
                .map(EventoOperacional::getId).toList();
        var estados = new HashMap<Long, EstadoOperacional>();
        if (!ids.isEmpty()) falhas.findAllById(ids).forEach(f -> estados.put(f.getId(), f.getEstadoAposReparo()));
        return registros.stream().map(e -> EventoOperacionalMapper.toResponse(e, estados.get(e.getId()))).toList();
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
        var estado = evento.getCategoria() == EventoOperacional.Categoria.FALHA_EQUIPAMENTO
                ? falhas.findById(evento.getId()).map(FalhaEquipamento::getEstadoAposReparo).orElse(null) : null;
        return EventoOperacionalMapper.toResponse(evento, estado);
    }
}
