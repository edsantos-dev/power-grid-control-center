package br.com.powergrid.controlcenter.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import br.com.powergrid.controlcenter.support.AlertasDashboardFixture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class AlertasV11MigrationTest {
    @Test
    void deveClassificarAlertasExistentesSemAlterarHistoricoEProjetarQuatroOrigens() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("10").load().migrate();
            AlertasDashboardFixture.inserir(jdbc);
            var tabelas = List.of("alerta", "alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao",
                    "fonte_medicao", "medicao_linha", "medicao_transformador");
            var antes = new LinkedHashMap<String, List<java.util.Map<String, Object>>>();
            for (String tabela : tabelas) antes.put(tabela, jdbc.queryForList("SELECT * FROM " + tabela + " ORDER BY id"));

            Flyway.configure().dataSource(datasource).target("11").load().migrate();

            for (String tabela : tabelas) {
                var depois = jdbc.queryForList("SELECT * FROM " + tabela + " ORDER BY id");
                if (tabela.startsWith("alerta_")) depois.forEach(row -> row.remove("criticidade"));
                assertThat(depois).as(tabela).isEqualTo(antes.get(tabela));
            }
            assertThat(jdbc.queryForObject("SELECT criticidade FROM alerta_sobrecarga WHERE id = 1301", String.class)).isEqualTo("ALTA");
            assertThat(jdbc.queryForObject("SELECT criticidade FROM alerta_tensao WHERE id = 1301", String.class)).isEqualTo("ALTA");
            assertThat(jdbc.queryForObject("SELECT criticidade FROM alerta_comunicacao WHERE id = 1301", String.class)).isEqualTo("MEDIA");
            assertThat(jdbc.queryForObject("SELECT criticidade FROM alerta WHERE id = 1301", String.class)).isEqualTo("BAIXA");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vw_alertas_dashboard", Long.class)).isEqualTo(4);
            assertThat(jdbc.queryForList("SELECT tipo_alerta FROM vw_alertas_dashboard", String.class))
                    .containsExactlyInAnyOrder("GENERICO", "SOBRECARGA", "TENSAO", "COMUNICACAO");
        } finally {
            datasource.destroy();
        }
    }
}
