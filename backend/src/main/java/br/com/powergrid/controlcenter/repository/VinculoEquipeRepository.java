package br.com.powergrid.controlcenter.repository;

import java.util.List;
import br.com.powergrid.controlcenter.model.VinculoEquipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VinculoEquipeRepository extends JpaRepository<VinculoEquipe, Long> {
    List<VinculoEquipe> findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(Long equipeId);
    Page<VinculoEquipe> findByEquipeIdAndFimVigenciaIsNull(Long equipeId, Pageable pageable);
    boolean existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(Long equipeId, Long usuarioId);
}
