package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.SituacaoPerdaTecnica;

public record PontoHistoricoDTO(OffsetDateTime inicioPeriodo, OffsetDateTime fimPeriodo,
        BigDecimal energiaInjetadaMwh, BigDecimal perdaEstimadaMwh, BigDecimal percentualPerdaPonderado,
        long quantidadeLeituras, SituacaoPerdaTecnica situacao) { }
