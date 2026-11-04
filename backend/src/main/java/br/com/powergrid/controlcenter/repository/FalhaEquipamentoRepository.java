package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.FalhaEquipamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FalhaEquipamentoRepository extends JpaRepository<FalhaEquipamento, Long> {
}
