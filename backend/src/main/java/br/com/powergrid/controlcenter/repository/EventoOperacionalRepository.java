package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.EventoOperacional;
import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOperacionalRepository extends JpaRepository<EventoOperacional, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoOperacional e where e.id = :id")
    Optional<EventoOperacional> buscarPorIdParaTransicao(@Param("id") Long id);
    String FILTROS_MANUTENCAO = " WHERE e.ativo_id = :ativoId AND e.categoria = 'MANUTENCAO'"
            + " AND (CAST(:situacao AS VARCHAR(32)) IS NULL OR e.situacao = :situacao)"
            + " AND (CAST(:responsavelUsuarioId AS BIGINT) IS NULL OR e.responsavel_usuario_id = :responsavelUsuarioId)"
            + " AND (CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE) IS NULL OR e.inicio_efetivo >= :dataInicio)"
            + " AND (CAST(:dataFim AS TIMESTAMP WITH TIME ZONE) IS NULL OR e.inicio_efetivo < :dataFim)";

    @Query(value = "SELECT e.* FROM evento_operacional e" + FILTROS_MANUTENCAO
            + " ORDER BY e.inicio_efetivo DESC NULLS LAST, e.id DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<EventoOperacional> consultarManutencoesPorAtivo(@Param("ativoId") Long ativoId,
            @Param("situacao") String situacao, @Param("responsavelUsuarioId") Long responsavelUsuarioId,
            @Param("dataInicio") OffsetDateTime dataInicio, @Param("dataFim") OffsetDateTime dataFim,
            @Param("limite") int limite, @Param("deslocamento") long deslocamento);

    @Query(value = "SELECT COUNT(*) FROM evento_operacional e" + FILTROS_MANUTENCAO, nativeQuery = true)
    long contarManutencoesPorAtivo(@Param("ativoId") Long ativoId,
            @Param("situacao") String situacao, @Param("responsavelUsuarioId") Long responsavelUsuarioId,
            @Param("dataInicio") OffsetDateTime dataInicio, @Param("dataFim") OffsetDateTime dataFim);

    @Transactional(readOnly = true)
    default Page<EventoOperacional> buscarManutencoesPorAtivo(Long ativoId, String situacao, Long responsavelUsuarioId,
            OffsetDateTime dataInicio, OffsetDateTime dataFim, Pageable pageable) {
        if (pageable.isUnpaged()) throw new IllegalArgumentException("A consulta de manutenções exige paginação.");
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        long total = contarManutencoesPorAtivo(ativoId, situacao, responsavelUsuarioId, dataInicio, dataFim);
        List<EventoOperacional> eventos = List.of();
        if (pagina.getOffset() < total) {
            eventos = consultarManutencoesPorAtivo(ativoId, situacao, responsavelUsuarioId,
                    dataInicio, dataFim, pagina.getPageSize(), pagina.getOffset());
        }
        return new PageImpl<>(eventos, pagina, total);
    }
}
