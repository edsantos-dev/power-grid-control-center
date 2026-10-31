package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.AlertaTensao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaTensaoRepository extends JpaRepository<AlertaTensao, Long> {
}
