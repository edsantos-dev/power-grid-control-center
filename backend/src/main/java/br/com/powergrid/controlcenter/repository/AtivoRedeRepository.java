package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.geografia.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AtivoRedeRepository extends Repository<AtivoRede, Long> {
    boolean existsById(Long id);

    @Query("select new br.com.powergrid.controlcenter.geografia.AtivoPontualMapaProjection("
            + "a.id, a.nome, a.estadoOperacional, type(a), a.latitude, a.longitude)"
            + " from AtivoRede a where type(a) in :tipos and a.estadoOperacional in :estados"
            + " and a.id > :aposId and a.latitude is not null and a.longitude is not null"
            + " and a.latitude between :minLat and :maxLat and a.longitude between :minLng and :maxLng"
            + " order by a.id asc")
    List<AtivoPontualMapaProjection> consultarPontos(@Param("tipos") Set<Class<? extends AtivoRede>> tipos,
            @Param("estados") Set<EstadoOperacional> estados,
            @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
            @Param("aposId") long aposId, Pageable pagina);

    default List<AtivoPontualMapaProjection> buscarPontos(BoundingBox caixa, Set<TipoAtivoMapa> tipos,
            Set<EstadoOperacional> estados, long aposId, Pageable pagina) {
        Set<Class<? extends AtivoRede>> classes = new java.util.HashSet<>();
        for (var tipo : tipos) if (tipo != TipoAtivoMapa.LINHA) classes.add(tipo.classe());
        if (classes.isEmpty() || estados.isEmpty()) return List.of();
        return consultarPontos(classes, estados, caixa.minLat(), caixa.maxLat(), caixa.minLng(), caixa.maxLng(), aposId, pagina);
    }
}
