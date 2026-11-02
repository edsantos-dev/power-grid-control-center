package br.com.powergrid.controlcenter.repository.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MedicaoEnergiaHistoricoProjection(OffsetDateTime inicioPeriodo, OffsetDateTime fimPeriodo,
        BigDecimal energiaInjetadaMwh, BigDecimal percentualPerdaReferencia) { }
