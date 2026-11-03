package br.com.powergrid.controlcenter.service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.HistoricoDesignacaoEquipe;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.HistoricoDesignacaoEquipeRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoDesignacaoEquipeService {
    private final DesignacaoEquipeService designacoes;
    private final EventoOperacionalService eventos;
    private final EventoOperacionalRepository eventosRepository;
    private final HistoricoDesignacaoEquipeRepository historicos;

    public HistoricoDesignacaoEquipeService(DesignacaoEquipeService designacoes, EventoOperacionalService eventos,
            EventoOperacionalRepository eventosRepository, HistoricoDesignacaoEquipeRepository historicos) {
        this.designacoes = designacoes;
        this.eventos = eventos;
        this.eventosRepository = eventosRepository;
        this.historicos = historicos;
    }

    @Transactional
    public EventoOperacionalResponseDTO designar(Long eventoId, DesignacaoEquipeRequestDTO request, Long usuarioId) {
        var evento = designacoes.designar(eventoId, request.equipeId(), request.versaoDesignacaoEsperada(),
                request.comentario(), usuarioId);
        return eventos.buscarPorId(evento.getId());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponseDTO<HistoricoDesignacaoResponseDTO> listar(Long eventoId, MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) {
            if (!Set.of("page", "size", "sort").contains(nome)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro não suportado no histórico: " + nome);
            }
        }
        var pagina = ParametrosConsultaMedicao.from(params, "registradoEm").pageable();
        if (!eventosRepository.existsById(eventoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado.");
        }
        long total = historicos.countByEventoOperacionalId(eventoId);
        List<HistoricoDesignacaoResponseDTO> conteudo = List.of();
        if (pagina.getOffset() < total) {
            conteudo = historicos.buscarPorEventoOrdenado(eventoId, pagina.getPageSize(), pagina.getOffset())
                    .stream().map(this::toResponse).toList();
        }
        return PageResponseDTO.from(new PageImpl<>(conteudo, pagina, total));
    }

    private HistoricoDesignacaoResponseDTO toResponse(HistoricoDesignacaoEquipe h) {
        return new HistoricoDesignacaoResponseDTO(h.getId(), h.getEventoOperacional().getId(),
                h.getEquipeAnterior() == null ? null : h.getEquipeAnterior().getId(),
                h.getEquipeNova() == null ? null : h.getEquipeNova().getId(),
                h.getUsuario().getId(), h.getRegistradoEm().withOffsetSameInstant(ZoneOffset.UTC),
                h.getComentario(), h.getVersaoDesignacao());
    }
}
