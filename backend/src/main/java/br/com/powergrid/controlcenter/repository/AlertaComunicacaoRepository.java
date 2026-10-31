package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.AlertaComunicacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaComunicacaoRepository extends JpaRepository<AlertaComunicacao, Long> {
}
