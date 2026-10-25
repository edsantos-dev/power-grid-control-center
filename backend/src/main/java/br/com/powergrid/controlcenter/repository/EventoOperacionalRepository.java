package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.EventoOperacional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOperacionalRepository extends JpaRepository<EventoOperacional, Long> {
}
