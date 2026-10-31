package br.com.powergrid.controlcenter.support;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AlertasDashboardFixture {
    public static final long ID_ALERTA = 1301;
    public static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-01-02T12:00:00.123456Z");

    private AlertasDashboardFixture() {
    }

    public static void inserir(JdbcTemplate jdbc) {
        for (long id : new long[] {1001, 1002, 1003, 1004, 1005}) {
            jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (?, ?, 'OPERACIONAL')", id, "Ativo " + id);
        }
        jdbc.update("INSERT INTO subestacao (id) VALUES (1001), (1002)");
        jdbc.update("INSERT INTO linha_transmissao (id, subestacao_origem_id, subestacao_destino_id, tensao_operacao_kv) VALUES (1003, 1001, 1002, 100)");
        jdbc.update("INSERT INTO alimentador (id, subestacao_id) VALUES (1004, 1001)");
        jdbc.update("INSERT INTO transformador (id, alimentador_id, capacidade_mva) VALUES (1005, 1004, 10)");
        jdbc.update("INSERT INTO fonte_medicao (id, identificador, ativo_id, origem, monitorada_desde, ultima_comunicacao_em) VALUES (1101, 'DASHBOARD-LINHA', 1003, 'SIMULADA', ?, ?)", INSTANTE.minusDays(1), INSTANTE.plusMinutes(1));
        jdbc.update("INSERT INTO fonte_medicao (id, identificador, ativo_id, origem, monitorada_desde, ultima_comunicacao_em) VALUES (1102, 'DASHBOARD-TRAFO', 1005, 'SIMULADA', ?, ?)", INSTANTE.minusDays(1), INSTANTE);
        jdbc.update("INSERT INTO medicao_linha (id, fonte_id, medido_em, recebido_em, tensao_kv) VALUES (1201, 1101, ?, ?, 90)", INSTANTE.minusSeconds(1), INSTANTE);
        jdbc.update("INSERT INTO medicao_transformador (id, fonte_id, medido_em, recebido_em, potencia_aparente_mva, capacidade_referencia_mva) VALUES (1202, 1102, ?, ?, 12, 10)", INSTANTE.minusSeconds(1), INSTANTE);
        jdbc.update("INSERT INTO alerta (id, tipo, criticidade, escopo, situacao, gerado_em) VALUES (1301, 'Aviso geral', 'BAIXA', 'REDE_MONITORADA', 'RESOLVIDO', ?)", INSTANTE);
        Long usuario = jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class);
        jdbc.update("INSERT INTO alerta_sobrecarga (id, ativo_id, medicao_id, carga_percentual, limiar_percentual, status, gerado_em, reconhecido_em, reconhecido_por_usuario_id) VALUES (1301, 1005, 1202, 120, 100, 'RECONHECIDO', ?, ?, ?)", INSTANTE, INSTANTE.plusMinutes(1), usuario);
        jdbc.update("INSERT INTO alerta_tensao (id, ativo_id, medicao_id, tipo, tensao_medida_kv, tensao_referencia_kv, limite_inferior_kv, limite_superior_kv, status, gerado_em) VALUES (1301, 1003, 1201, 'SUBTENSAO', 90, 100, 95, 105, 'ATIVO', ?)", INSTANTE);
        jdbc.update("INSERT INTO alerta_comunicacao (id, fonte_id, ativo_id, monitorada_desde, ultima_comunicacao_em, tolerancia_segundos, detectado_em, status, restabelecido_em) VALUES (1301, 1101, 1003, ?, ?, 300, ?, 'RESTABELECIDO', ?)", INSTANTE.minusDays(1), INSTANTE.minusHours(1), INSTANTE, INSTANTE.plusMinutes(1));
    }
}
