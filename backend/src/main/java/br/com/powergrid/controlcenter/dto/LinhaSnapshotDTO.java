package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;

import br.com.powergrid.controlcenter.model.SituacaoFatorPotencia;

public record LinhaSnapshotDTO(Long ativoId, String nome, Tipo tipoAtivo, Long subestacaoOrigemId, Long subestacaoDestinoId,
        String origemLeitura, Long terminalReferenciaId, BigDecimal tensaoOperacao,
        AmostraSnapshotDTO<BigDecimal> tensaoKv, AmostraSnapshotDTO<BigDecimal> correnteA,
        AmostraSnapshotDTO<BigDecimal> frequenciaHz, AmostraSnapshotDTO<BigDecimal> potenciaAtivaMw,
        AmostraSnapshotDTO<BigDecimal> potenciaReativaMvar, AmostraSnapshotDTO<BigDecimal> fatorPotencia,
        SituacaoFatorPotencia situacaoFatorPotencia) implements AtivoSnapshotDTO {
}
