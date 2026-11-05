package br.com.powergrid.controlcenter.repository;

import java.util.List;

import br.com.powergrid.controlcenter.dto.ConsumidorResumoResponseDTO;
import br.com.powergrid.controlcenter.model.Consumidor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumidorRepository extends JpaRepository<Consumidor, Long> {
    boolean existsByDocumento(String documento);

    List<ConsumidorResumoResponseDTO> findAllProjectedBy();
}
