package br.com.powergrid.controlcenter.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import br.com.powergrid.controlcenter.model.EquipeManutencao;
import br.com.powergrid.controlcenter.model.SituacaoEquipe;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EquipeManutencaoRepository extends JpaRepository<EquipeManutencao, Long>,
        JpaSpecificationExecutor<EquipeManutencao> {

    default Page<EquipeManutencao> buscarFiltradas(String nome, SituacaoEquipe situacao, Pageable pageable) {
        Specification<EquipeManutencao> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (nome != null && !nome.isBlank()) {
                String trecho = nome.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(builder.like(builder.lower(root.get("nome")), "%" + trecho + "%", '!'));
            }
            if (situacao != null) predicates.add(builder.equal(root.get("situacao"), situacao));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
