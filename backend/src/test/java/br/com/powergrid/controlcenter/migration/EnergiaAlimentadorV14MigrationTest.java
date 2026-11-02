package br.com.powergrid.controlcenter.migration;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class EnergiaAlimentadorV14MigrationTest {
    @Test
    void devePreservarCadastrosEFontesSemInventarPercentualDePerda() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:energia_v14_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("13").load().migrate();
            jdbc.update("insert into ativo_rede (id, nome, estado_operacional) values (9001, 'Subestação', 'OPERACIONAL')");
            jdbc.update("insert into subestacao (id) values (9001)");
            jdbc.update("insert into ativo_rede (id, nome, estado_operacional) values (9002, 'Alimentador existente', 'OPERACIONAL')");
            jdbc.update("insert into alimentador (id, tensao_nominal_kv, subestacao_id) values (9002, 13.8, 9001)");
            jdbc.update("insert into fonte_medicao (identificador, ativo_id, origem, monitorada_desde, ultima_comunicacao_em) values ('FONTE-V14', 9002, 'SIMULADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            var ativosAntes = jdbc.queryForList("select * from ativo_rede order by id");
            var alimentadorAntes = jdbc.queryForMap("select * from alimentador where id = 9002");
            var fontesAntes = jdbc.queryForList("select * from fonte_medicao order by id");
            Flyway.configure().dataSource(datasource).target("14").load().migrate();
            assertThat(jdbc.queryForList("select * from ativo_rede order by id")).isEqualTo(ativosAntes);
            assertThat(jdbc.queryForMap("select id, tensao_nominal_kv, subestacao_id from alimentador where id = 9002")).isEqualTo(alimentadorAntes);
            assertThat(jdbc.queryForObject("select percentual_perda_tecnica from alimentador where id = 9002", java.math.BigDecimal.class)).isNull();
            assertThat(jdbc.queryForList("select * from fonte_medicao order by id")).isEqualTo(fontesAntes);
            assertThat(jdbc.queryForObject("select count(*) from medicao_energia_alimentador", Long.class)).isZero();
        } finally { datasource.destroy(); }
    }
}
