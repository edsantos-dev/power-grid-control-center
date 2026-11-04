package br.com.powergrid.controlcenter.repository;

import java.util.List;
import java.util.Optional;
import br.com.powergrid.controlcenter.model.HistoricoAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface HistoricoAlertaRepository extends Repository<HistoricoAlerta, Long> {
    @Transactional
    <S extends HistoricoAlerta> S saveAndFlush(S historico);

    Optional<HistoricoAlerta> findById(Long id);
    Optional<HistoricoAlerta> findByIdAndAlertaId(Long id, Long alertaId);
    long countByAlertaId(Long alertaId);

    Page<HistoricoAlerta> findByAlertaIdOrderByAlteradoEmDescIdDesc(Long alertaId, Pageable pageable);

    @Query(value = "SELECT * FROM historico_alerta WHERE alerta_id = :alertaId"
            + " ORDER BY alterado_em DESC, id DESC LIMIT :limite OFFSET :deslocamento", nativeQuery = true)
    List<HistoricoAlerta> buscarPorAlertaOrdenado(@Param("alertaId") Long alertaId,
            @Param("limite") int limite, @Param("deslocamento") long deslocamento);
}
