package br.com.powergrid.controlcenter.repository;

import java.util.Optional;
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

    Page<HistoricoDesignacaoEquipe> findByEventoOperacionalIdOrderByRegistradoEmDescIdDesc(
            Long eventoOperacionalId, Pageable pageable);
}
