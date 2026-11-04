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
    @Query(value = "SELECT COUNT(*) > 0 FROM evento_operacional WHERE ativo_id = :ativoId"
            + " AND categoria = 'FALHA_EQUIPAMENTO' AND (situacao IS NULL OR situacao <> 'CONCLUIDO')", nativeQuery = true)
    boolean existeFalhaNaoConcluida(@Param("ativoId") Long ativoId);

    @Query(value = "SELECT COUNT(*) > 0 FROM evento_operacional WHERE ativo_id = :ativoId AND id <> :eventoId"
            + " AND categoria = 'FALHA_EQUIPAMENTO' AND (situacao IS NULL OR situacao <> 'CONCLUIDO')", nativeQuery = true)
    boolean existeOutraFalhaNaoConcluida(@Param("ativoId") Long ativoId, @Param("eventoId") Long eventoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoOperacional e where e.id = :id")
    Optional<EventoOperacional> buscarPorIdParaTransicao(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoOperacional e where e.id = :id")
    Optional<EventoOperacional> buscarPorIdParaDesignacao(@Param("id") Long id);

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

    Optional<EventoOperacional> findByIdAndCategoria(Long id, EventoOperacional.Categoria categoria);

    String FILTROS_INTERRUPCAO = " WHERE e.categoria = 'INTERRUPCAO_FORNECIMENTO'"
            + " AND (CAST(:ativoId AS BIGINT) IS NULL OR e.ativo_id = :ativoId)"
            + " AND (CAST(:situacao AS VARCHAR(32)) IS NULL OR e.situacao = :situacao)"
            + " AND (CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE) IS NULL OR e.inicio_efetivo >= :dataInicio)"
            + " AND (CAST(:dataFim AS TIMESTAMP WITH TIME ZONE) IS NULL OR e.inicio_efetivo < :dataFim)";

    @Query(value = "SELECT e.* FROM evento_operacional e" + FILTROS_INTERRUPCAO
            + " ORDER BY e.inicio_efetivo DESC NULLS LAST, e.id DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<EventoOperacional> consultarInterrupcoes(@Param("ativoId") Long ativoId, @Param("situacao") String situacao,
            @Param("dataInicio") OffsetDateTime dataInicio, @Param("dataFim") OffsetDateTime dataFim,
            @Param("limite") int limite, @Param("deslocamento") long deslocamento);

    @Query(value = "SELECT COUNT(*) FROM evento_operacional e" + FILTROS_INTERRUPCAO, nativeQuery = true)
    long contarInterrupcoes(@Param("ativoId") Long ativoId, @Param("situacao") String situacao,
            @Param("dataInicio") OffsetDateTime dataInicio, @Param("dataFim") OffsetDateTime dataFim);

    @Transactional(readOnly = true)
    default Page<EventoOperacional> buscarInterrupcoes(Long ativoId, String situacao, OffsetDateTime dataInicio,
            OffsetDateTime dataFim, Pageable pageable) {
        if (pageable.isUnpaged()) throw new IllegalArgumentException("A consulta de interrupções exige paginação.");
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        long total = contarInterrupcoes(ativoId, situacao, dataInicio, dataFim);
        List<EventoOperacional> eventos = List.of();
        if (pagina.getOffset() < total) {
            eventos = consultarInterrupcoes(ativoId, situacao, dataInicio, dataFim, pagina.getPageSize(), pagina.getOffset());
        }
        return new PageImpl<>(eventos, pagina, total);
    }
}
