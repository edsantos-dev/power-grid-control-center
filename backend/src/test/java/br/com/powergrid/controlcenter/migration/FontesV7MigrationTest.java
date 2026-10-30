package br.com.powergrid.controlcenter.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class FontesV7MigrationTest {
    @Test
    void devePreservarFonteELeiturasDaV6AoMigrarParaV7() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("6").load().migrate();
            for (long id : new long[] {101, 102, 103}) {
                jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (?, ?, 'OPERACIONAL')", id, "Ativo " + id);
            }
            jdbc.update("INSERT INTO subestacao (id) VALUES (101), (102)");
            jdbc.update("INSERT INTO linha_transmissao (id, subestacao_origem_id, subestacao_destino_id) VALUES (103, 101, 102)");
            jdbc.update("INSERT INTO fonte_medicao (id, identificador, linha_id, origem, subestacao_referencia_id) VALUES (201, 'LEGADO', 103, 'SIMULADA', 101)");
            var instante = OffsetDateTime.parse("2026-10-30T12:00:00Z");
            jdbc.update("INSERT INTO medicao_linha (id, fonte_id, medido_em, recebido_em, tensao_kv) VALUES (301, 201, ?, ?, 138)", instante, instante);
            Flyway.configure().dataSource(datasource).target("7").load().migrate();
            assertThat(jdbc.queryForObject("SELECT ativo_id FROM fonte_medicao WHERE id = 201", Long.class)).isEqualTo(103L);
            assertThat(jdbc.queryForObject("SELECT identificador FROM fonte_medicao WHERE id = 201", String.class)).isEqualTo("LEGADO");
            assertThat(jdbc.queryForObject("SELECT subestacao_referencia_id FROM fonte_medicao WHERE id = 201", Long.class)).isEqualTo(101L);
            assertThat(jdbc.queryForObject("SELECT fonte_id FROM medicao_linha WHERE id = 301", Long.class)).isEqualTo(201L);
            assertThat(jdbc.queryForObject("SELECT tensao_kv FROM medicao_linha WHERE id = 301", java.math.BigDecimal.class)).isEqualByComparingTo("138");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medicao_transformador", Long.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }
}
