package br.com.powergrid.controlcenter.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import br.com.powergrid.controlcenter.dto.AlertaDashboardResponseDTO;
import br.com.powergrid.controlcenter.dto.AlertaAtivoResponseDTO;
import br.com.powergrid.controlcenter.model.AlertaDashboardView;
import br.com.powergrid.controlcenter.repository.projection.AlertaAtivoProjection;

public final class AlertaDashboardMapper {
    private AlertaDashboardMapper() { }

    public static AlertaAtivoResponseDTO toAtivoResponse(AlertaAtivoProjection projecao) {
        var a = toResponse(projecao.alerta());
        return new AlertaAtivoResponseDTO(
                a.idOrigem(),
                a.tipoAlerta(),
                a.criticidade(),
                a.escopo(),
                a.ativoId(),
                a.fonteId(),
                a.medicaoId(),
                a.ocorridoEm(),
                a.statusOrigem(),
                a.subtipo(),
                a.mensagem(),
                a.reconhecidoEm(),
                a.reconhecidoPorUsuarioId(),
                a.restabelecidoEm(),
                a.cargaPercentual(),
                a.limiarPercentual(),
                a.tensaoMedidaKv(),
                a.tensaoReferenciaKv(),
                a.limiteInferiorKv(),
                a.limiteSuperiorKv(),
                a.monitoradaDesde(),
                a.ultimaComunicacaoEm(),
                a.toleranciaSegundos(),
                a.descricao(),
                a.resolvidoEm(),
                a.resolvidoPorUsuarioId(),
                projecao.tipoAtivo());
    }

    public static AlertaDashboardResponseDTO toResponse(AlertaDashboardView a) {
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
                descricao(a),
                utc(a.getResolvidoEm()),
                a.getResolvidoPorUsuarioId());
    }

    private static String descricao(AlertaDashboardView a) {
        return switch (a.getTipoAlerta()) {
            case GENERICO -> a.getMensagem();
            case SOBRECARGA -> "Sobrecarga de " + numero(a.getCargaPercentual()) + "% (limiar: " + numero(a.getLimiarPercentual()) + "%)";
            case TENSAO -> ("SUBTENSAO".equals(a.getSubtipo()) ? "Subtensão: " : "Sobretensão: ")
                    + numero(a.getTensaoMedidaKv()) + " kV (referência: " + numero(a.getTensaoReferenciaKv())
                    + " kV; faixa: " + numero(a.getLimiteInferiorKv()) + " a " + numero(a.getLimiteSuperiorKv()) + " kV)";
            case COMUNICACAO -> "Perda de comunicação na fonte " + a.getFonteId() + " (tolerância: " + a.getToleranciaSegundos() + " s)";
        };
    }

    private static String numero(BigDecimal valor) {
        var formato = new DecimalFormat("0.00######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        formato.setRoundingMode(RoundingMode.HALF_EVEN);
        return formato.format(valor);
    }

    private static OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }


}
