package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.AlertaSobrecarga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaSobrecargaRepository extends JpaRepository<AlertaSobrecarga, Long> {
}
