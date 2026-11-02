package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import br.com.powergrid.controlcenter.model.MedicaoEnergiaAlimentador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicaoEnergiaAlimentadorRepository extends JpaRepository<MedicaoEnergiaAlimentador, Long>, JpaSpecificationExecutor<MedicaoEnergiaAlimentador> {
    @Query("select (count(m) > 0) from MedicaoEnergiaAlimentador m"
            + " where m.fonte.ativo.id = :alimentadorId"
            + " and :novoInicio < m.fimPeriodo and :novoFim > m.inicioPeriodo")
    boolean existeSobreposicaoPorAlimentador(@Param("alimentadorId") Long alimentadorId,
            @Param("novoInicio") OffsetDateTime novoInicio, @Param("novoFim") OffsetDateTime novoFim);

    @Query("select m from MedicaoEnergiaAlimentador m join fetch m.fonte f"
            + " where f.ativo.id = :alimentadorId and m.inicioPeriodo >= :inicio and m.fimPeriodo <= :fim"
            + " order by m.inicioPeriodo asc, m.id asc")
    List<MedicaoEnergiaAlimentador> buscarParaCalculo(@Param("alimentadorId") Long alimentadorId,
            @Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    @Override
    @EntityGraph(attributePaths = "fonte")
    Page<MedicaoEnergiaAlimentador> findAll(Specification<MedicaoEnergiaAlimentador> filtro, Pageable pageable);

    default Page<MedicaoEnergiaAlimentador> buscarPorAlimentadorEPeriodo(Long alimentadorId,
            OffsetDateTime inicio, OffsetDateTime fim, Pageable pageable) {
        Specification<MedicaoEnergiaAlimentador> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("fonte").get("ativo").get("id"), alimentadorId));
            if (inicio != null) predicates.add(builder.greaterThanOrEqualTo(root.get("inicioPeriodo"), inicio));
            if (fim != null) predicates.add(builder.lessThan(root.get("inicioPeriodo"), fim));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
