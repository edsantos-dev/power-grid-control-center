package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;

public record TransformadorSnapshotDTO(Long ativoId, String nome, Tipo tipoAtivo, Long subestacaoId,
        AmostraSnapshotDTO<BigDecimal> potenciaAparenteMva, AmostraSnapshotDTO<BigDecimal> capacidadeReferenciaMva,
        AmostraSnapshotDTO<BigDecimal> cargaPercentual) implements AtivoSnapshotDTO {
}
