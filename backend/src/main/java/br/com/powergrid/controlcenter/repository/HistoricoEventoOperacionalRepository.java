package br.com.powergrid.controlcenter.repository;

import java.util.Optional;
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
    Page<HistoricoEventoOperacional> findByEventoId(Long eventoId, Pageable pageable);
}
