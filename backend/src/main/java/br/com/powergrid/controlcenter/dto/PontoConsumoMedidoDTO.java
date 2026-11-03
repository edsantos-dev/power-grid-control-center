package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.SituacaoConsumoMedido;

public record PontoConsumoMedidoDTO(OffsetDateTime inicioPeriodo, OffsetDateTime fimPeriodo,
        BigDecimal consumoMedidoMwh, long quantidadeLeituras, long quantidadeMedidoresComCoberturaCompleta,
        SituacaoConsumoMedido situacao) { }
