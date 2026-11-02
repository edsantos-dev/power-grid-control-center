package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import br.com.powergrid.controlcenter.model.TipoAtivoIndicador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.projection.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class IndicadoresRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public IndicadoresRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    private static final String ATIVOS_TIPADOS = """
            WITH ativos_tipados AS (
                SELECT a.id, a.estado_operacional, 'SUBESTACAO' AS tipo,
                       s.id AS subestacao_id, CAST(NULL AS BIGINT) AS outra_subestacao_id
                FROM ativo_rede a JOIN subestacao s ON s.id = a.id
                UNION ALL
                SELECT a.id, a.estado_operacional, 'ALIMENTADOR', al.subestacao_id, CAST(NULL AS BIGINT)
                FROM ativo_rede a JOIN alimentador al ON al.id = a.id
                UNION ALL
                SELECT a.id, a.estado_operacional, 'TRANSFORMADOR', al.subestacao_id, CAST(NULL AS BIGINT)
                FROM ativo_rede a JOIN transformador t ON t.id = a.id
                JOIN alimentador al ON al.id = t.alimentador_id
                UNION ALL
                SELECT a.id, a.estado_operacional, 'LINHA', l.subestacao_origem_id, l.subestacao_destino_id
                FROM ativo_rede a JOIN linha_transmissao l ON l.id = a.id
                UNION ALL
                SELECT a.id, a.estado_operacional, 'USINA', u.subestacao_id, CAST(NULL AS BIGINT)
                FROM ativo_rede a JOIN usina_geradora u ON u.id = a.id
            ), ativos_elegiveis AS (
                SELECT id, estado_operacional, tipo FROM ativos_tipados WHERE 1 = 1
            """;

    public List<InventarioIndicadoresProjection> consultarInventario(Long ativoId,
            Set<TipoAtivoIndicador> tipos, Long subestacaoId) {
        var parametros = new MapSqlParameterSource();
        String escopo = escopo(ativoId, tipos, subestacaoId, parametros);
        return jdbc.query(escopo + """
                SELECT tipo, estado_operacional, COUNT(*) AS quantidade
                FROM ativos_elegiveis GROUP BY tipo, estado_operacional
                ORDER BY tipo, estado_operacional
                """, parametros, (rs, linha) -> new InventarioIndicadoresProjection(
                        TipoAtivoIndicador.valueOf(rs.getString("tipo")),
                        EstadoOperacional.valueOf(rs.getString("estado_operacional")), rs.getLong("quantidade")));
    }

    public RestabelecimentoIndicadoresProjection consultarRestabelecimento(Long ativoId,
            Set<TipoAtivoIndicador> tipos, Long subestacaoId, OffsetDateTime dataInicio, OffsetDateTime dataFim) {
        var parametros = periodo(dataInicio, dataFim);
        String escopo = escopo(ativoId, tipos, subestacaoId, parametros);
        return jdbc.queryForObject(escopo + """
                SELECT COUNT(*) AS quantidade,
                       COALESCE(SUM(EXTRACT(EPOCH FROM e.termino_efetivo)
                                    - EXTRACT(EPOCH FROM e.inicio_efetivo)), 0) AS duracao_total,
                       AVG(EXTRACT(EPOCH FROM e.termino_efetivo)
                           - EXTRACT(EPOCH FROM e.inicio_efetivo)) AS tempo_medio
                FROM evento_operacional e JOIN ativos_elegiveis a ON a.id = e.ativo_id
                WHERE e.categoria = 'INTERRUPCAO_FORNECIMENTO' AND e.situacao = 'CONCLUIDO'
                  AND e.inicio_efetivo IS NOT NULL AND e.termino_efetivo >= e.inicio_efetivo
                  AND e.termino_efetivo >= :dataInicio AND e.termino_efetivo < :dataFim
                """, parametros, (rs, linha) -> new RestabelecimentoIndicadoresProjection(
                        rs.getLong("quantidade"), rs.getBigDecimal("duracao_total"), rs.getBigDecimal("tempo_medio")));
    }

    public TempoInterrompidoProjection consultarTempoInterrompido(Long ativoId,
            Set<TipoAtivoIndicador> tipos, Long subestacaoId, OffsetDateTime dataInicio, OffsetDateTime dataFim) {
        var parametros = periodo(dataInicio, dataFim);
        String escopo = escopo(ativoId, tipos, subestacaoId, parametros);
        // O máximo acumulado mantém intervalos aninhados no mesmo grupo; LAG isolado não basta.
        return jdbc.queryForObject(escopo + """
                , recortes AS (
                    SELECT e.id AS evento_id, e.ativo_id,
                           CASE WHEN e.inicio_efetivo < :dataInicio
                                THEN CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE) ELSE e.inicio_efetivo END AS inicio,
                           CASE WHEN e.termino_efetivo IS NULL OR e.termino_efetivo > :dataFim
                                THEN CAST(:dataFim AS TIMESTAMP WITH TIME ZONE) ELSE e.termino_efetivo END AS fim
                    FROM evento_operacional e JOIN ativos_elegiveis a ON a.id = e.ativo_id
                    WHERE e.categoria = 'INTERRUPCAO_FORNECIMENTO'
                      AND e.inicio_efetivo < :dataFim
                      AND (e.termino_efetivo IS NULL OR e.termino_efetivo > :dataInicio)
                      AND (e.termino_efetivo IS NULL OR e.termino_efetivo >= e.inicio_efetivo)
                ), antecedentes AS (
                    SELECT evento_id, ativo_id, inicio, fim,
                           MAX(fim) OVER (PARTITION BY ativo_id ORDER BY inicio, fim, evento_id
                               ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS maior_fim_anterior
                    FROM recortes WHERE fim > inicio
                ), marcacoes AS (
                    SELECT evento_id, ativo_id, inicio, fim,
                           CASE WHEN maior_fim_anterior IS NULL OR inicio > maior_fim_anterior
                                THEN 1 ELSE 0 END AS novo_grupo
                    FROM antecedentes
                ), grupos AS (
                    SELECT ativo_id, inicio, fim,
                           SUM(novo_grupo) OVER (PARTITION BY ativo_id ORDER BY inicio, fim, evento_id
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS grupo
                    FROM marcacoes
                ), unificados AS (
                    SELECT ativo_id, grupo, MIN(inicio) AS inicio, MAX(fim) AS fim
                    FROM grupos GROUP BY ativo_id, grupo
                )
                SELECT COUNT(DISTINCT ativo_id) AS ativos, COUNT(*) AS intervalos,
                       COALESCE(SUM(EXTRACT(EPOCH FROM fim) - EXTRACT(EPOCH FROM inicio)), 0) AS segundos
                FROM unificados
                """, parametros, (rs, linha) -> new TempoInterrompidoProjection(
                        rs.getLong("ativos"), rs.getLong("intervalos"), rs.getBigDecimal("segundos")));
    }

    private String escopo(Long ativoId, Set<TipoAtivoIndicador> tipos, Long subestacaoId,
            MapSqlParameterSource parametros) {
        var sql = new StringBuilder(ATIVOS_TIPADOS);
        if (ativoId != null) { sql.append(" AND id = :ativoId"); parametros.addValue("ativoId", ativoId); }
        if (subestacaoId != null) {
            sql.append(" AND (subestacao_id = :subestacaoId OR outra_subestacao_id = :subestacaoId)");
            parametros.addValue("subestacaoId", subestacaoId);
        }
        if (tipos != null) {
            if (tipos.isEmpty()) sql.append(" AND 1 = 0");
            else {
                sql.append(" AND tipo IN (:tipos)");
                parametros.addValue("tipos", tipos.stream().map(Enum::name).toList());
            }
        }
        return sql.append(")\n").toString();
    }

    private MapSqlParameterSource periodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio == null || fim == null || !inicio.toInstant().isBefore(fim.toInstant())) {
            throw new IllegalArgumentException("O período deve ter início anterior ao fim.");
        }
        return new MapSqlParameterSource("dataInicio", inicio).addValue("dataFim", fim);
    }
}
