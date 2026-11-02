package br.com.powergrid.controlcenter.repository;

import java.util.Optional;
import br.com.powergrid.controlcenter.model.Alimentador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlimentadorRepository extends JpaRepository<Alimentador, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alimentador a where a.id = :id")
    Optional<Alimentador> buscarPorIdComBloqueio(@Param("id") Long id);
}
