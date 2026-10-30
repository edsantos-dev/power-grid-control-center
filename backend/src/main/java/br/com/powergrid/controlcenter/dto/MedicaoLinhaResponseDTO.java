package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;
import br.com.powergrid.controlcenter.model.SituacaoFatorPotencia;

public record MedicaoLinhaResponseDTO(Long id, Long linhaId, Long fonteId, OrigemMedicao origem,
        OffsetDateTime medidoEm, OffsetDateTime recebidoEm, BigDecimal tensaoKv, BigDecimal correnteA,
        BigDecimal potenciaAtivaMw, BigDecimal potenciaReativaMvar, Long subestacaoReferenciaId,
        BigDecimal frequenciaHz, BigDecimal fatorPotencia, SituacaoFatorPotencia situacaoFatorPotencia) {
}
