package br.com.powergrid.controlcenter.service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.HistoricoEventoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.dto.TransacaoEventoRequestDTO;
import br.com.powergrid.controlcenter.model.HistoricoEventoOperacional;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoEventoOperacionalService {
    private final EventoOperacionalService eventos;
    private final EventoOperacionalRepository eventosRepository;
    private final HistoricoEventoOperacionalRepository historicos;

    public HistoricoEventoOperacionalService(EventoOperacionalService eventos,
            EventoOperacionalRepository eventosRepository, HistoricoEventoOperacionalRepository historicos) {
        this.eventos = eventos;
        this.eventosRepository = eventosRepository;
        this.historicos = historicos;
    }

    @Transactional
    public HistoricoEventoResponseDTO transicionar(Long eventoId, TransacaoEventoRequestDTO request) {
        Long historicoId = eventos.transicionar(eventoId, request.situacaoEsperada(), request.situacaoDestino(), request.comentario());
        return toResponse(historicos.findByIdAndEventoId(historicoId, eventoId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<HistoricoEventoResponseDTO> listar(Long eventoId, MultiValueMap<String, String> params) {
        validarEvento(eventoId);
        for (String nome : params.keySet()) {
            if (!Set.of("page", "size", "sort").contains(nome)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro não suportado no histórico: " + nome);
            }
        }
        var pagina = ParametrosConsultaMedicao.from(params, "alteradoEm").pageable();
        long total = historicos.countByEventoId(eventoId);
        List<HistoricoEventoResponseDTO> conteudo = List.of();
        if (pagina.getOffset() < total) {
            conteudo = historicos.buscarPorEventoOrdenado(eventoId, pagina.getPageSize(), pagina.getOffset())
                    .stream().map(this::toResponse).toList();
        }
        return PageResponseDTO.from(new PageImpl<>(conteudo, pagina, total));
    }

    @Transactional(readOnly = true)
    public HistoricoEventoResponseDTO buscarPorId(Long eventoId, Long transicaoId) {
        validarEvento(eventoId);
        return toResponse(historicos.findByIdAndEventoId(transicaoId, eventoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transição não encontrada para este evento.")));
    }

    private void validarEvento(Long eventoId) {
        if (!eventosRepository.existsById(eventoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado.");
        }
    }

    private HistoricoEventoResponseDTO toResponse(HistoricoEventoOperacional h) {
        return new HistoricoEventoResponseDTO(h.getId(), h.getEvento().getId(), h.getUsuario().getId(),
                h.getSituacaoAnterior(), h.getSituacaoNova(), h.getAlteradoEm().withOffsetSameInstant(ZoneOffset.UTC), h.getComentario());
    }
}
