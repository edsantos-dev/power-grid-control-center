package br.com.powergrid.controlcenter.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

import br.com.powergrid.controlcenter.model.MedidorInteligente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedidorInteligenteRepository extends JpaRepository<MedidorInteligente, Long> {
    boolean existsByNumeroSerie(String numeroSerie);

    @EntityGraph(attributePaths = {"consumidor", "transformador.alimentador.subestacao"})
    List<MedidorInteligente> findByConsumidorIdOrderByIdAsc(Long consumidorId);
}
