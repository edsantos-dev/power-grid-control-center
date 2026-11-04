package br.com.powergrid.controlcenter.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class HistoricoAlertaV17MigrationTest {
    @Test
    void devePreservarAlertasLegadosSemInventarAutoresDatasOuHistorico() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("16").load().migrate();
            String[] situacoes = {"ABERTO", "RECONHECIDO", "RESOLVIDO", null, "IMPORTADO"};
            var gerado = OffsetDateTime.parse("2026-11-01T10:00:00.123456-03:00");
            for (int i = 0; i < situacoes.length; i++) {
                jdbc.update("INSERT INTO alerta (id, tipo, criticidade, gerado_em, situacao, escopo)"
                                + " VALUES (?, 'Alerta legado', 'ALTA', ?, ?, 'REDE_MONITORADA')",
                        1001L + i, i == 3 ? null : gerado, situacoes[i]);
            }
            var antes = jdbc.queryForList("SELECT * FROM alerta ORDER BY id");
            var dashboardAntes = jdbc.queryForList("SELECT * FROM vw_alertas_dashboard ORDER BY tipo_alerta, id_origem");

            Flyway.configure().dataSource(datasource).target("17").load().migrate();

            assertThat(jdbc.queryForList("SELECT * FROM alerta ORDER BY id")).isEqualTo(antes);
            assertThat(jdbc.queryForList("SELECT * FROM vw_alertas_dashboard ORDER BY tipo_alerta, id_origem")).isEqualTo(dashboardAntes);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_alerta", Long.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }
}
