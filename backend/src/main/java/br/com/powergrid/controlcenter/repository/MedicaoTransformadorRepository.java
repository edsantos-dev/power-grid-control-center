package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import br.com.powergrid.controlcenter.model.MedicaoTransformador;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicaoTransformadorRepository extends JpaRepository<MedicaoTransformador, Long>, JpaSpecificationExecutor<MedicaoTransformador> {
    default Page<MedicaoTransformador> buscarPorTransformadorEPeriodo(Long transformadorId, OffsetDateTime inicio,
            OffsetDateTime fim, Pageable pageable) {
        Specification<MedicaoTransformador> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("fonte").get("ativo").get("id"), transformadorId));
            if (inicio != null) predicates.add(builder.greaterThanOrEqualTo(root.get("medidoEm"), inicio));
            if (fim != null) predicates.add(builder.lessThan(root.get("medidoEm"), fim));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
