package br.com.powergrid.controlcenter.repository;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import br.com.powergrid.controlcenter.model.AlertaDashboardId;
import br.com.powergrid.controlcenter.model.AlertaDashboardView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AlertaDashboardViewRepository extends Repository<AlertaDashboardView, AlertaDashboardId> {
    String FILTROS = " WHERE (CAST(:tipoAlerta AS VARCHAR(20)) IS NULL OR tipo_alerta = :tipoAlerta)"
            + " AND (CAST(:criticidade AS VARCHAR(32)) IS NULL OR criticidade = :criticidade)"
            + " AND (CAST(:escopo AS VARCHAR(20)) IS NULL OR escopo = :escopo)"
            + " AND (CAST(:ativoId AS BIGINT) IS NULL OR ativo_id = :ativoId)"
            + " AND (CAST(:fonteId AS BIGINT) IS NULL OR fonte_id = :fonteId)"
            + " AND (CAST(:statusOrigem AS VARCHAR(32)) IS NULL OR status_origem = :statusOrigem)"
            + " AND (CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE) IS NULL OR ocorrido_em >= :dataInicio)"
            + " AND (CAST(:dataFim AS TIMESTAMP WITH TIME ZONE) IS NULL OR ocorrido_em < :dataFim)";

    @Query(value = "SELECT * FROM vw_alertas_dashboard" + FILTROS
            + " ORDER BY ocorrido_em DESC NULLS LAST, tipo_alerta ASC, id_origem DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<AlertaDashboardView> buscarFiltrados(@Param("tipoAlerta") String tipoAlerta,
            @Param("criticidade") String criticidade, @Param("escopo") String escopo,
            @Param("ativoId") Long ativoId, @Param("fonteId") Long fonteId,
            @Param("statusOrigem") String statusOrigem, @Param("dataInicio") OffsetDateTime dataInicio,
            @Param("dataFim") OffsetDateTime dataFim, @Param("limite") int limite,
            @Param("deslocamento") long deslocamento);

    @Query(value = "SELECT COUNT(*) FROM vw_alertas_dashboard" + FILTROS, nativeQuery = true)
    long contarFiltrados(@Param("tipoAlerta") String tipoAlerta,
            @Param("criticidade") String criticidade, @Param("escopo") String escopo,
            @Param("ativoId") Long ativoId, @Param("fonteId") Long fonteId,
            @Param("statusOrigem") String statusOrigem, @Param("dataInicio") OffsetDateTime dataInicio,
            @Param("dataFim") OffsetDateTime dataFim);

    Optional<AlertaDashboardView> findById(AlertaDashboardId id);
    List<AlertaDashboardView> findAll();
    Page<AlertaDashboardView> findAll(Pageable pageable);
    long count();
}
