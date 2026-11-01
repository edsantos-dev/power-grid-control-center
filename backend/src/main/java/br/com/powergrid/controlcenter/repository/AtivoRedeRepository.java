package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.AtivoRede;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AtivoRedeRepository extends Repository<AtivoRede, Long> {
    boolean existsById(Long id);
}
