package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.EventoOperacional;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOperacionalRepository extends JpaRepository<EventoOperacional, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoOperacional e where e.id = :id")
    Optional<EventoOperacional> buscarPorIdParaTransicao(@Param("id") Long id);
}
