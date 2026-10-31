package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.TipoAlertaDashboard;

public record AlertaDashboardResponseDTO(
        Long idOrigem,
        TipoAlertaDashboard tipoAlerta,
        Criticidade criticidade,
        EscopoAlerta escopo,
        Long ativoId,
        Long fonteId,
        Long medicaoId,
        OffsetDateTime ocorridoEm,
        String statusOrigem,
        String subtipo,
        String mensagem,
        OffsetDateTime reconhecidoEm,
        Long reconhecidoPorUsuarioId,
        OffsetDateTime restabelecidoEm,
        BigDecimal cargaPercentual,
        BigDecimal limiarPercentual,
        BigDecimal tensaoMedidaKv,
        BigDecimal tensaoReferenciaKv,
        BigDecimal limiteInferiorKv,
        BigDecimal limiteSuperiorKv,
        OffsetDateTime monitoradaDesde,
        OffsetDateTime ultimaComunicacaoEm,
        Integer toleranciaSegundos,
        String descricao) {
}
