package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;





import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import br.com.powergrid.controlcenter.model.AlertaTensao;
import br.com.powergrid.controlcenter.model.StatusAlerta;
import br.com.powergrid.controlcenter.model.TipoAlertaTensao;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertaTensaoRepository extends JpaRepository<AlertaTensao, Long>, JpaSpecificationExecutor<AlertaTensao> {
    default Page<AlertaTensao> buscarFiltrados(Long ativoId, TipoAlertaTensao tipo, StatusAlerta status, OffsetDateTime inicio,
            OffsetDateTime fim, Pageable pageable) {
        Specification<AlertaTensao> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (ativoId != null) predicates.add(builder.equal(root.get("ativo").get("id"), ativoId));
            if (tipo != null) predicates.add(builder.equal(root.get("tipo"), tipo));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (inicio != null) predicates.add(builder.greaterThanOrEqualTo(root.get("geradoEm"), inicio));
            if (fim != null) predicates.add(builder.lessThan(root.get("geradoEm"), fim));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
