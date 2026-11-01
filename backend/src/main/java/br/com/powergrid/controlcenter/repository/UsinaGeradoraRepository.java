package br.com.powergrid.controlcenter.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.TipoGeracao;
import br.com.powergrid.controlcenter.model.UsinaGeradora;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsinaGeradoraRepository extends JpaRepository<UsinaGeradora, Long>, JpaSpecificationExecutor<UsinaGeradora> {
    default Page<UsinaGeradora> buscarFiltradas(TipoGeracao tipoGeracao, Long subestacaoId, Pageable pageable) {
        return buscarFiltradas(null, tipoGeracao, null, subestacaoId, pageable);
    }

    default Page<UsinaGeradora> buscarFiltradas(String nome, TipoGeracao tipoGeracao, EstadoOperacional estado,
            Long subestacaoId, Pageable pageable) {
        Specification<UsinaGeradora> filtro = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (nome != null && !nome.isBlank()) {
                String trecho = nome.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(builder.like(builder.lower(root.get("nome")), "%" + trecho + "%", '!'));
            }
            if (estado != null) predicates.add(builder.equal(root.get("estadoOperacional"), estado));
            if (tipoGeracao != null) predicates.add(builder.equal(root.get("tipoGeracao"), tipoGeracao));
            if (subestacaoId != null) predicates.add(builder.equal(root.get("subestacao").get("id"), subestacaoId));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = count(filtro);
            if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        }
        return findAll(filtro, pageable);
    }
}
