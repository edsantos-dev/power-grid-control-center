package br.com.powergrid.controlcenter.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.powergrid.controlcenter.model.HistoricoEventoOperacional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface HistoricoEventoOperacionalRepository extends Repository<HistoricoEventoOperacional, Long> {
    @Transactional
    <S extends HistoricoEventoOperacional> S saveAndFlush(S historico);

    Optional<HistoricoEventoOperacional> findById(Long id);
    Optional<HistoricoEventoOperacional> findByIdAndEventoId(Long id, Long eventoId);
    long countByEventoId(Long eventoId);

    @Query(value = "SELECT * FROM historico_evento_operacional WHERE evento_id = :eventoId"
            + " ORDER BY alterado_em DESC, id DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<HistoricoEventoOperacional> buscarPorEventoOrdenado(@Param("eventoId") Long eventoId,
            @Param("limite") int limite, @Param("deslocamento") long deslocamento);
    Page<HistoricoEventoOperacional> findByEventoId(Long eventoId, Pageable pageable);
    @Query("select h from HistoricoEventoOperacional h where h.evento.id in :eventoIds"
            + " and not exists (select posterior.id from HistoricoEventoOperacional posterior"
            + " where posterior.evento.id = h.evento.id"
            + " and (posterior.alteradoEm > h.alteradoEm"
            + " or (posterior.alteradoEm = h.alteradoEm and posterior.id > h.id)))"
            + " order by h.alteradoEm desc, h.id desc")
    List<HistoricoEventoOperacional> consultarUltimasTransicoes(@Param("eventoIds") List<Long> eventoIds);

    default List<HistoricoEventoOperacional> buscarUltimasTransicoes(List<Long> eventoIds) {
        if (eventoIds.isEmpty()) return List.of();
        return consultarUltimasTransicoes(eventoIds);
    }
}
