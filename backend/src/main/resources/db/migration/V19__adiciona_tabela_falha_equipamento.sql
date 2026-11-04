CREATE TABLE falha_equipamento (
    evento_operacional_id BIGINT PRIMARY KEY,
    estado_anterior VARCHAR(20) NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    estado_apos_reparo VARCHAR(20),
    CONSTRAINT fk_falha_equipamento_evento FOREIGN KEY (evento_operacional_id)
        REFERENCES evento_operacional (id),
    CONSTRAINT ck_falha_equipamento_estado_anterior CHECK (
        estado_anterior IN ('OPERACIONAL', 'FALHA', 'MANUTENCAO')
    ),
    CONSTRAINT ck_falha_equipamento_estado_apos_reparo CHECK (
        estado_apos_reparo IS NULL OR estado_apos_reparo IN ('OPERACIONAL', 'MANUTENCAO')
    )
);
