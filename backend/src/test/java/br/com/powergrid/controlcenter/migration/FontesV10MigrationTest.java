package br.com.powergrid.controlcenter.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class FontesV10MigrationTest {
    @Test
    void devePreservarFontesELeiturasERecuperarUltimoRecebimentoAoMigrarParaV10() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("9").load().migrate();
            for (long id : new long[] {101, 102, 103, 104, 105}) {
                jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (?, ?, 'OPERACIONAL')", id, "Ativo " + id);
            }
            jdbc.update("INSERT INTO subestacao (id) VALUES (101), (102)");
            jdbc.update("INSERT INTO linha_transmissao (id, subestacao_origem_id, subestacao_destino_id) VALUES (103, 101, 102)");
            jdbc.update("INSERT INTO alimentador (id, subestacao_id) VALUES (104, 101)");
            jdbc.update("INSERT INTO transformador (id, alimentador_id, capacidade_mva) VALUES (105, 104, 10)");
            jdbc.update("INSERT INTO fonte_medicao (id, identificador, ativo_id, origem, subestacao_referencia_id) VALUES (201, 'LINHA', 103, 'SIMULADA', 101)");
            jdbc.update("INSERT INTO fonte_medicao (id, identificador, ativo_id, origem) VALUES (202, 'TRAFO', 105, 'SIMULADA'), (203, 'SEM-LEITURA', 103, 'SIMULADA')");
            var antigo = OffsetDateTime.parse("2026-01-01T12:00:00.123456Z");
            var recente = OffsetDateTime.parse("2026-01-01T10:00:00.654321-03:00");
            jdbc.update("INSERT INTO medicao_linha (id, fonte_id, medido_em, recebido_em, tensao_kv) VALUES (301, 201, ?, ?, 138.123456)", antigo, recente);
            jdbc.update("INSERT INTO medicao_linha (id, fonte_id, medido_em, recebido_em, corrente_a) VALUES (302, 201, ?, ?, 50)", recente, antigo);
            jdbc.update("INSERT INTO medicao_transformador (id, fonte_id, medido_em, recebido_em, potencia_aparente_mva, capacidade_referencia_mva) VALUES (401, 202, ?, ?, 12, 10)", antigo, recente.plusSeconds(10));
            jdbc.update("INSERT INTO medicao_transformador (id, fonte_id, medido_em, recebido_em, potencia_aparente_mva, capacidade_referencia_mva) VALUES (402, 202, ?, ?, 8, 10)", recente, antigo);
            var fontesAntes = jdbc.queryForList("SELECT id, identificador, ativo_id, origem, subestacao_referencia_id FROM fonte_medicao ORDER BY id");
            var linhasAntes = jdbc.queryForList("SELECT * FROM medicao_linha ORDER BY id");
            var trafosAntes = jdbc.queryForList("SELECT * FROM medicao_transformador ORDER BY id");
            var inicio = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).toInstant();

            Flyway.configure().dataSource(datasource).target("10").load().migrate();

            var fim = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).toInstant();
            assertThat(jdbc.queryForList("SELECT id, identificador, ativo_id, origem, subestacao_referencia_id FROM fonte_medicao ORDER BY id")).isEqualTo(fontesAntes);
            assertThat(jdbc.queryForList("SELECT * FROM medicao_linha ORDER BY id")).isEqualTo(linhasAntes);
            assertThat(jdbc.queryForList("SELECT * FROM medicao_transformador ORDER BY id")).isEqualTo(trafosAntes);
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = 201", OffsetDateTime.class).toInstant()).isEqualTo(recente.toInstant());
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = 202", OffsetDateTime.class).toInstant()).isEqualTo(recente.plusSeconds(10).toInstant());
            assertThat(jdbc.queryForObject("SELECT ultima_comunicacao_em FROM fonte_medicao WHERE id = 203", OffsetDateTime.class)).isNull();
            for (var inicioMonitoramento : jdbc.queryForList("SELECT monitorada_desde FROM fonte_medicao", OffsetDateTime.class)) {
                assertThat(inicioMonitoramento.toInstant()).isBetween(inicio, fim);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta_comunicacao", Long.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }
}
