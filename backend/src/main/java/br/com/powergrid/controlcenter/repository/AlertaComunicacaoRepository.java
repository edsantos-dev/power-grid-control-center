package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import br.com.powergrid.controlcenter.model.AlertaComunicacao;
import br.com.powergrid.controlcenter.model.StatusComunicacao;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertaComunicacaoRepository extends JpaRepository<AlertaComunicacao, Long>, JpaSpecificationExecutor<AlertaComunicacao> {
    boolean existsByFonteIdAndStatus(Long fonteId, StatusComunicacao status);

    default Page<AlertaComunicacao> buscarFiltrados(Long fonteId, Long ativoId, StatusComunicacao status, OffsetDateTime inicio,
            OffsetDateTime fim, Pageable pageable) {
        Specification<AlertaComunicacao> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (ativoId != null) predicates.add(builder.equal(root.get("ativo").get("id"), ativoId));
            if (fonteId != null) predicates.add(builder.equal(root.get("fonte").get("id"), fonteId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (inicio != null) predicates.add(builder.greaterThanOrEqualTo(root.get("detectadoEm"), inicio));
            if (fim != null) predicates.add(builder.lessThan(root.get("detectadoEm"), fim));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
