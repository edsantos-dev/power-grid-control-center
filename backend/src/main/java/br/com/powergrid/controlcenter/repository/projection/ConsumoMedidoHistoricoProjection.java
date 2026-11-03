package br.com.powergrid.controlcenter.repository.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ConsumoMedidoHistoricoProjection(Long medidorId, OffsetDateTime inicioPeriodo,
        OffsetDateTime fimPeriodo, BigDecimal consumoKwh) { }
