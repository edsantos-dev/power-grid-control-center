package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.MedidorInteligente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedidorInteligenteRepository extends JpaRepository<MedidorInteligente, Long> {
    boolean existsByNumeroSerie(String numeroSerie);
}
