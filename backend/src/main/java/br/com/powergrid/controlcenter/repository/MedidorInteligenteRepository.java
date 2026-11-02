package br.com.powergrid.controlcenter.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import br.com.powergrid.controlcenter.model.MedidorInteligente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedidorInteligenteRepository extends JpaRepository<MedidorInteligente, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MedidorInteligente m where m.id = :id")
    Optional<MedidorInteligente> buscarPorIdComBloqueio(@Param("id") Long id);

    @EntityGraph(attributePaths = {"consumidor", "transformador.alimentador.subestacao"})
    @Query("select m from MedidorInteligente m where m.id = :id")
    Optional<MedidorInteligente> buscarComTopologia(@Param("id") Long id);

    boolean existsByNumeroSerie(String numeroSerie);

    @EntityGraph(attributePaths = {"consumidor", "transformador.alimentador.subestacao"})
    List<MedidorInteligente> findByConsumidorIdOrderByIdAsc(Long consumidorId);
}
