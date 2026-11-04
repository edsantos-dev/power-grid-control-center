package br.com.powergrid.controlcenter.service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.HistoricoAlerta;
import br.com.powergrid.controlcenter.repository.AlertaRepository;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoricoAlertaService {
    private final TratativaAlertaService tratativas;
    private final AlertaService alertas;
    private final AlertaRepository alertasRepository;
    private final HistoricoAlertaRepository historicos;

    public HistoricoAlertaService(TratativaAlertaService tratativas, AlertaService alertas,
            AlertaRepository alertasRepository, HistoricoAlertaRepository historicos) {
        this.tratativas = tratativas;
        this.alertas = alertas;
        this.alertasRepository = alertasRepository;
        this.historicos = historicos;
    }

    @Transactional
    public AlertaResponseDTO reconhecer(Long id, Long usuarioId, TratativaAlertaRequestDTO request) {
        tratativas.reconhecer(id, usuarioId, request.comentario());
        return alertas.obterPorId(id);
    }

    @Transactional
    public AlertaResponseDTO resolver(Long id, Long usuarioId, TratativaAlertaRequestDTO request) {
        tratativas.resolver(id, usuarioId, request.comentario());
        return alertas.obterPorId(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponseDTO<HistoricoAlertaResponseDTO> listar(Long id, MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) {
            if (!Set.of("page", "size", "sort").contains(nome)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro não suportado no histórico: " + nome);
            }
        }
        var pagina = ParametrosConsultaMedicao.from(params, "alteradoEm").pageable();
        if (!alertasRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta não encontrado.");
        long total = historicos.countByAlertaId(id);
        List<HistoricoAlertaResponseDTO> conteudo = List.of();
        if (pagina.getOffset() < total) conteudo = historicos.buscarPorAlertaOrdenado(id, pagina.getPageSize(), pagina.getOffset())
                .stream().map(this::toResponse).toList();
        return PageResponseDTO.from(new PageImpl<>(conteudo, pagina, total));
    }

    private HistoricoAlertaResponseDTO toResponse(HistoricoAlerta h) {
        return new HistoricoAlertaResponseDTO(h.getId(), h.getAlerta().getId(), h.getUsuario().getId(),
                h.getSituacaoAnterior(), h.getSituacaoNova(), h.getAlteradoEm().withOffsetSameInstant(ZoneOffset.UTC), h.getComentario());
    }
}
