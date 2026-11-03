package br.com.powergrid.controlcenter.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.powergrid.controlcenter.model.HistoricoDesignacaoEquipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface HistoricoDesignacaoEquipeRepository extends Repository<HistoricoDesignacaoEquipe, Long> {
    @Transactional
    <S extends HistoricoDesignacaoEquipe> S saveAndFlush(S historico);

    Optional<HistoricoDesignacaoEquipe> findById(Long id);
    Optional<HistoricoDesignacaoEquipe> findByIdAndEventoOperacionalId(Long id, Long eventoOperacionalId);
    long countByEventoOperacionalId(Long eventoOperacionalId);

    @Query(value = "SELECT * FROM historico_designacao_equipe WHERE evento_operacional_id = :eventoId"
            + " ORDER BY registrado_em DESC, id DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<HistoricoDesignacaoEquipe> buscarPorEventoOrdenado(@Param("eventoId") Long eventoId,
            @Param("limite") int limite, @Param("deslocamento") long deslocamento);

    Page<HistoricoDesignacaoEquipe> findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(
            Long eventoOperacionalId, Pageable pageable);
}
