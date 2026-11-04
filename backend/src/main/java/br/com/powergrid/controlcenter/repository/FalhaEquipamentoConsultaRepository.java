package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import br.com.powergrid.controlcenter.model.FalhaEquipamento;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class FalhaEquipamentoConsultaRepository {
    private final EntityManager em;
    public FalhaEquipamentoConsultaRepository(EntityManager em) { this.em = em; }

    public Page<FalhaEquipamento> buscar(Long ativoId, String situacao, OffsetDateTime inicio, OffsetDateTime fim, Pageable pageable) {
        var cb = em.getCriteriaBuilder();
        var count = cb.createQuery(Long.class); var c = count.from(FalhaEquipamento.class);
        count.select(cb.count(c)).where(filtros(c, cb, ativoId, situacao, inicio, fim));
        long total = em.createQuery(count).getSingleResult();
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (pagina.getOffset() >= total) return new PageImpl<>(List.of(), pagina, total);
        var query = cb.createQuery(FalhaEquipamento.class); var f = query.from(FalhaEquipamento.class);
        var evento = f.fetch("eventoOperacional", JoinType.INNER);
        evento.fetch("ativo", JoinType.INNER); evento.fetch("registradorUsuario", JoinType.INNER);
        evento.fetch("responsavelUsuario", JoinType.INNER); evento.fetch("equipeDesignada", JoinType.LEFT);
        query.select(f).where(filtros(f, cb, ativoId, situacao, inicio, fim))
                .orderBy(cb.desc(f.get("registradoEm")), cb.desc(f.get("id")));
        var registros = em.createQuery(query).setFirstResult(Math.toIntExact(pagina.getOffset()))
                .setMaxResults(pagina.getPageSize()).getResultList();
        return new PageImpl<>(registros, pagina, total);
    }

    private Predicate[] filtros(Root<FalhaEquipamento> f, CriteriaBuilder cb, Long ativoId, String situacao,
            OffsetDateTime inicio, OffsetDateTime fim) {
        var filtros = new ArrayList<Predicate>(); var evento = f.get("eventoOperacional");
        filtros.add(cb.equal(evento.get("categoria"), EventoOperacional.Categoria.FALHA_EQUIPAMENTO));
        if (ativoId != null) filtros.add(cb.equal(evento.get("ativo").get("id"), ativoId));
        if (situacao != null) filtros.add(cb.equal(evento.get("situacao"), situacao));
        if (inicio != null) filtros.add(cb.greaterThanOrEqualTo(f.get("registradoEm"), inicio));
        if (fim != null) filtros.add(cb.lessThan(f.get("registradoEm"), fim));
        return filtros.toArray(Predicate[]::new);
    }
}
