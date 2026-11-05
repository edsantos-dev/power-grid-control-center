package br.com.powergrid.controlcenter.dto;

public sealed interface AtivoSnapshotDTO permits LinhaSnapshotDTO, TransformadorSnapshotDTO {
    Long ativoId();
    String nome();
    Tipo tipoAtivo();

    enum Tipo {
        LINHA, TRANSFORMADOR
    }
}
