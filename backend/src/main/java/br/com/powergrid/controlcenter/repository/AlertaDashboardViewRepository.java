package br.com.powergrid.controlcenter.repository;

import java.util.List;
import java.util.Optional;
import br.com.powergrid.controlcenter.model.AlertaDashboardId;
import br.com.powergrid.controlcenter.model.AlertaDashboardView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AlertaDashboardViewRepository extends Repository<AlertaDashboardView, AlertaDashboardId> {
    Optional<AlertaDashboardView> findById(AlertaDashboardId id);
    List<AlertaDashboardView> findAll();
    Page<AlertaDashboardView> findAll(Pageable pageable);
    long count();
}
