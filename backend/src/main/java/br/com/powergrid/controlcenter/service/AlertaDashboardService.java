package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.AlertaDashboardResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.AlertaDashboardViewRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaDashboardService {
    private static final Set<String> STATUS = Set.of("ABERTO", "ATIVO", "RECONHECIDO", "RESOLVIDO", "RESTABELECIDO");
    private final AlertaDashboardViewRepository repository;

    public AlertaDashboardService(AlertaDashboardViewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AlertaDashboardResponseDTO> listar(MultiValueMap<String, String> params) {
        var consulta = ParametrosConsultaMedicao.from(params, "ocorridoEm");
        var pagina = PageRequest.of(consulta.pageable().getPageNumber(), consulta.pageable().getPageSize());
        var tipo = enumeracao(params, "tipoAlerta", TipoAlertaDashboard.class);
        var criticidade = enumeracao(params, "criticidade", Criticidade.class);
        var escopo = enumeracao(params, "escopo", EscopoAlerta.class);
        var ativoId = identificador(params, "ativoId");
        var fonteId = identificador(params, "fonteId");
        var status = ParametrosConsultaMedicao.parametro(params, "statusOrigem");
        if (status != null && !STATUS.contains(status)) throw invalido("Status de origem inválido.");
        long total = repository.contarFiltrados(tipo, criticidade, escopo, ativoId, fonteId, status, consulta.inicio(), consulta.fim());
        List<AlertaDashboardResponseDTO> conteudo = List.of();
        if (pagina.getOffset() < total) {
            conteudo = repository.buscarFiltrados(tipo, criticidade, escopo, ativoId, fonteId, status,
                    consulta.inicio(), consulta.fim(), pagina.getPageSize(), pagina.getOffset()).stream().map(this::toResponse).toList();
        }
        return PageResponseDTO.from(new PageImpl<>(conteudo, pagina, total));
    }

    private <E extends Enum<E>> String enumeracao(MultiValueMap<String, String> params, String nome, Class<E> tipo) {
        var valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try { return Enum.valueOf(tipo, valor).name(); }
        catch (IllegalArgumentException ex) { throw invalido("Valor inválido para " + nome + "."); }
    }

    private Long identificador(MultiValueMap<String, String> params, String nome) {
        var valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) { throw invalido(nome + " deve ser um inteiro positivo válido."); }
    }

    private AlertaDashboardResponseDTO toResponse(AlertaDashboardView a) {
        return new AlertaDashboardResponseDTO(
                a.getIdOrigem(),
                a.getTipoAlerta(),
                a.getCriticidade(),
                a.getEscopo(),
                a.getAtivoId(),
                a.getFonteId(),
                a.getMedicaoId(),
                utc(a.getOcorridoEm()),
                a.getStatusOrigem(),
                a.getSubtipo(),
                a.getMensagem(),
                utc(a.getReconhecidoEm()),
                a.getReconhecidoPorUsuarioId(),
                utc(a.getRestabelecidoEm()),
                a.getCargaPercentual(),
                a.getLimiarPercentual(),
                a.getTensaoMedidaKv(),
                a.getTensaoReferenciaKv(),
                a.getLimiteInferiorKv(),
                a.getLimiteSuperiorKv(),
                utc(a.getMonitoradaDesde()),
                utc(a.getUltimaComunicacaoEm()),
                a.getToleranciaSegundos(),
                descricao(a));
    }

    private String descricao(AlertaDashboardView a) {
        return switch (a.getTipoAlerta()) {
            case GENERICO -> a.getMensagem();
            case SOBRECARGA -> "Sobrecarga de " + numero(a.getCargaPercentual()) + "% (limiar: " + numero(a.getLimiarPercentual()) + "%)";
            case TENSAO -> ("SUBTENSAO".equals(a.getSubtipo()) ? "Subtensão: " : "Sobretensão: ")
                    + numero(a.getTensaoMedidaKv()) + " kV (referência: " + numero(a.getTensaoReferenciaKv())
                    + " kV; faixa: " + numero(a.getLimiteInferiorKv()) + " a " + numero(a.getLimiteSuperiorKv()) + " kV)";
            case COMUNICACAO -> "Perda de comunicação na fonte " + a.getFonteId() + " (tolerância: " + a.getToleranciaSegundos() + " s)";
        };
    }

    private String numero(BigDecimal valor) {
        var formato = new DecimalFormat("0.00######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        formato.setRoundingMode(RoundingMode.HALF_EVEN);
        return formato.format(valor);
    }

    private OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
