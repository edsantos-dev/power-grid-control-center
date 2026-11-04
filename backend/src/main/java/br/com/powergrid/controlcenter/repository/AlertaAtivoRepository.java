package br.com.powergrid.controlcenter.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.projection.AlertaAtivoProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AlertaAtivoRepository {
    private final EntityManager em;

    public AlertaAtivoRepository(EntityManager em) {
        this.em = em;
    }

    public Page<AlertaAtivoProjection> buscar(FiltroAlertasAtivos filtro, Pageable pageable) {
        Objects.requireNonNull(filtro, "Filtro obrigatório.");
        Objects.requireNonNull(pageable, "Paginação obrigatória.");
        if (pageable.isUnpaged()) throw new IllegalArgumentException("A consulta de alertas ativos exige paginação.");
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        var cb = em.getCriteriaBuilder();

        var contagem = cb.createQuery(Long.class);
        var raizContagem = contagem.from(AlertaDashboardView.class);
        contagem.select(cb.count(raizContagem.get("idOrigem")))
                .where(predicados(contagem, raizContagem, cb, filtro));
        long total = em.createQuery(contagem).getSingleResult();
        if (pagina.getOffset() >= total) return new PageImpl<>(List.of(), pagina, total);
        int deslocamento = Math.toIntExact(pagina.getOffset());

        var consulta = cb.createTupleQuery();
        var raiz = consulta.from(AlertaDashboardView.class);
        consulta.select(cb.tuple(raiz.alias("alerta"), tipoAtivo(consulta, raiz, cb).alias("tipoAtivo")))
                .where(predicados(consulta, raiz, cb, filtro));
        var prioridade = cb.<Integer>selectCase()
                .when(cb.equal(raiz.get("criticidade"), Criticidade.CRITICA), 1)
                .when(cb.equal(raiz.get("criticidade"), Criticidade.ALTA), 2)
                .when(cb.equal(raiz.get("criticidade"), Criticidade.MEDIA), 3)
                .when(cb.equal(raiz.get("criticidade"), Criticidade.BAIXA), 4)
                .otherwise(5);
        var instanteAusente = cb.<Integer>selectCase().when(cb.isNull(raiz.get("ocorridoEm")), 1).otherwise(0);
        consulta.orderBy(cb.asc(prioridade), cb.asc(instanteAusente), cb.desc(raiz.get("ocorridoEm")),
                cb.asc(raiz.get("tipoAlerta")), cb.desc(raiz.get("idOrigem")));
        var conteudo = em.createQuery(consulta).setFirstResult(deslocamento).setMaxResults(pagina.getPageSize())
                .getResultList().stream().map(this::projetar).toList();
        return new PageImpl<>(conteudo, pagina, total);
    }

    private Predicate[] predicados(AbstractQuery<?> consulta, Root<AlertaDashboardView> raiz,
            CriteriaBuilder cb, FiltroAlertasAtivos filtro) {
        var termos = new ArrayList<Predicate>();
        termos.add(cb.or(
                cb.and(cb.equal(raiz.get("tipoAlerta"), TipoAlertaDashboard.GENERICO),
                        raiz.get("statusOrigem").in("ABERTO", "RECONHECIDO")),
                cb.and(raiz.get("tipoAlerta").in(TipoAlertaDashboard.SOBRECARGA,
                                TipoAlertaDashboard.TENSAO, TipoAlertaDashboard.COMUNICACAO),
                        cb.equal(raiz.get("statusOrigem"), "ATIVO"))));
        if (filtro.tipoAlerta() != null) termos.add(cb.equal(raiz.get("tipoAlerta"), filtro.tipoAlerta()));
        if (filtro.criticidade() != null) termos.add(cb.equal(raiz.get("criticidade"), filtro.criticidade()));
        if (filtro.escopo() != null) termos.add(cb.equal(raiz.get("escopo"), filtro.escopo()));
        if (filtro.ativoId() != null) termos.add(cb.equal(raiz.get("ativoId"), filtro.ativoId()));
        if (filtro.fonteId() != null) termos.add(cb.equal(raiz.get("fonteId"), filtro.fonteId()));
        if (filtro.dataInicio() != null) termos.add(cb.greaterThanOrEqualTo(raiz.get("ocorridoEm"), filtro.dataInicio()));
        if (filtro.dataFim() != null) termos.add(cb.lessThan(raiz.get("ocorridoEm"), filtro.dataFim()));
        if (filtro.tipoAtivo() != null) termos.add(existeAtivo(consulta, raiz, cb, filtro.tipoAtivo(), null));
        if (filtro.subestacaoId() != null) {
            var origens = new ArrayList<Predicate>();
            for (var tipo : TipoAtivoAlerta.values()) origens.add(existeAtivo(consulta, raiz, cb, tipo, filtro.subestacaoId()));
            termos.add(cb.or(origens.toArray(Predicate[]::new)));
        }
        return termos.toArray(Predicate[]::new);
    }

    private Expression<String> tipoAtivo(AbstractQuery<?> consulta, Root<AlertaDashboardView> raiz, CriteriaBuilder cb) {
        var tipo = cb.<String>selectCase();
        for (var valor : TipoAtivoAlerta.values()) tipo.when(existeAtivo(consulta, raiz, cb, valor, null), valor.name());
        return tipo.otherwise(cb.nullLiteral(String.class));
    }

    private Predicate existeAtivo(AbstractQuery<?> consulta, Root<AlertaDashboardView> alerta,
            CriteriaBuilder cb, TipoAtivoAlerta tipo, Long subestacaoId) {
        var subconsulta = consulta.subquery(Integer.class);
        var ativo = subconsulta.from(classe(tipo));
        var termos = new ArrayList<Predicate>();
        termos.add(cb.equal(ativo.get("id"), alerta.get("ativoId")));
        if (subestacaoId != null) {
            Predicate pertence = switch (tipo) {
                case SUBESTACAO -> cb.equal(ativo.get("id"), subestacaoId);
                case ALIMENTADOR, USINA -> cb.equal(ativo.get("subestacao").get("id"), subestacaoId);
                case TRANSFORMADOR -> cb.equal(ativo.get("alimentador").get("subestacao").get("id"), subestacaoId);
                case MEDIDOR -> cb.equal(ativo.get("transformador").get("alimentador").get("subestacao").get("id"), subestacaoId);
                case LINHA -> cb.or(cb.equal(ativo.get("subestacaoOrigem").get("id"), subestacaoId),
                        cb.equal(ativo.get("subestacaoDestino").get("id"), subestacaoId));
            };
            termos.add(pertence);
        }
        subconsulta.select(cb.literal(1)).where(termos.toArray(Predicate[]::new));
        return cb.exists(subconsulta);
    }

    private Class<? extends AtivoRede> classe(TipoAtivoAlerta tipo) {
        return switch (tipo) {
            case SUBESTACAO -> Subestacao.class;
            case ALIMENTADOR -> Alimentador.class;
            case TRANSFORMADOR -> Transformador.class;
            case LINHA -> LinhaTransmissao.class;
            case USINA -> UsinaGeradora.class;
            case MEDIDOR -> MedidorInteligente.class;
        };
    }

    private AlertaAtivoProjection projetar(Tuple linha) {
        return new AlertaAtivoProjection(linha.get("alerta", AlertaDashboardView.class), linha.get("tipoAtivo", String.class));
    }
}
