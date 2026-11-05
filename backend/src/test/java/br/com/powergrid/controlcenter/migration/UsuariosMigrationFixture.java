package br.com.powergrid.controlcenter.migration;

import org.springframework.jdbc.core.JdbcTemplate;

final class UsuariosMigrationFixture {
    private UsuariosMigrationFixture() {
    }

    static Long operador(JdbcTemplate jdbc) {
        return criar(jdbc, "OPERADOR");
    }

    static Long criar(JdbcTemplate jdbc, String perfil) {
        String email = perfil.toLowerCase(java.util.Locale.ROOT) + "@migration.test";
        jdbc.update("INSERT INTO usuario (matricula, email, perfil) VALUES (?, ?, ?)", perfil, email, perfil);
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }
}
