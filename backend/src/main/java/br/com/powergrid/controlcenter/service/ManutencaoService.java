package br.com.powergrid.controlcenter.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.dto.HistoricoEventoResponseDTO;
import br.com.powergrid.controlcenter.dto.ManutencaoHistoricoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import br.com.powergrid.controlcenter.model.HistoricoEventoOperacional;
import br.com.powergrid.controlcenter.model.SituacaoEventoOperacional;
import br.com.powergrid.controlcenter.repository.AtivoRedeRepository;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.HistoricoEventoOperacionalRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManutencaoService {
    private static final Set<String> PARAMETROS = Set.of("page", "size", "sort", "situacao", "responsavelUsuarioId", "dataInicio", "dataFim");
    private final AtivoRedeRepository ativos;
    private final EventoOperacionalRepository eventos;
    private final HistoricoEventoOperacionalRepository historicos;

    public ManutencaoService(AtivoRedeRepository ativos, EventoOperacionalRepository eventos,
            HistoricoEventoOperacionalRepository historicos) {
        this.ativos = ativos; this.eventos = eventos; this.historicos = historicos;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<ManutencaoHistoricoResponseDTO> listar(Long ativoId, MultiValueMap<String, String> params) {
        if (!ativos.existsById(ativoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
        for (String nome : params.keySet()) {
            if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "inicioEfetivo");
        var situacao = ParametrosConsultaMedicao.parametro(params, "situacao");
        if (situacao != null) {
            try { SituacaoEventoOperacional.valueOf(situacao); }
            catch (IllegalArgumentException ex) { throw invalido("Situação de manutenção inválida."); }
        }
        var responsavel = responsavel(params);
        var pagina = eventos.buscarManutencoesPorAtivo(ativoId, situacao, responsavel,
                consulta.inicio(), consulta.fim(), consulta.pageable());
        var ids = pagina.getContent().stream().map(EventoOperacional::getId).toList();
        var ultimas = historicos.buscarUltimasTransicoes(ids).stream().collect(
                Collectors.toMap(h -> h.getEvento().getId(), Function.identity()));
        return PageResponseDTO.from(pagina.map(e -> new ManutencaoHistoricoResponseDTO(
                evento(e), historico(ultimas.get(e.getId())))));
    }

    private Long responsavel(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "responsavelUsuarioId");
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) { throw invalido("responsavelUsuarioId deve ser um inteiro positivo válido."); }
    }

    private EventoOperacionalResponseDTO evento(EventoOperacional e) {
        return new EventoOperacionalResponseDTO(e.getId(), e.getAtivo().getId(), e.getCategoria(), e.getDescricao(),
                e.getCriticidade(), e.getSituacao(), e.getRegistradorUsuario().getId(), e.getResponsavelUsuario().getId(),
                utc(e.getInicioPrevisto()), utc(e.getTerminoPrevisto()), utc(e.getInicioEfetivo()), utc(e.getTerminoEfetivo()));
    }

    private HistoricoEventoResponseDTO historico(HistoricoEventoOperacional h) {
        if (h == null) return null;
        return new HistoricoEventoResponseDTO(h.getId(), h.getEvento().getId(), h.getUsuario().getId(),
                h.getSituacaoAnterior(), h.getSituacaoNova(), utc(h.getAlteradoEm()), h.getComentario());
    }

    private OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
