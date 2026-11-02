package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.LinhaTransmissao;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.geografia.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinhaTransmissaoRepository extends JpaRepository<LinhaTransmissao, Long> {

    @Transactional(readOnly = true)
    @Query("select new br.com.powergrid.controlcenter.geografia.LinhaMapaProjection("
            + "l.id, l.nome, l.estadoOperacional, o.id, d.id, o.latitude, o.longitude, d.latitude, d.longitude)"
            + " from LinhaTransmissao l join l.subestacaoOrigem o join l.subestacaoDestino d"
            + " where l.estadoOperacional in :estados and l.id > :aposId"
            + " and o.latitude between -90 and 90 and o.longitude between -180 and 180"
            + " and d.latitude between -90 and 90 and d.longitude between -180 and 180"
            + " and (o.latitude <> d.latitude or o.longitude <> d.longitude)"
            + " and (o.latitude >= :minLat or d.latitude >= :minLat)"
            + " and (o.latitude <= :maxLat or d.latitude <= :maxLat)"
            + " and (o.longitude >= :minLng or d.longitude >= :minLng)"
            + " and (o.longitude <= :maxLng or d.longitude <= :maxLng)"
            + " order by l.id asc")
    List<LinhaMapaProjection> consultarCandidatas(@Param("estados") Set<EstadoOperacional> estados,
            @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
            @Param("aposId") long aposId, Pageable pagina);

    @Transactional(readOnly = true)
    default List<LinhaMapaProjection> buscarCandidatas(BoundingBox caixa, Set<EstadoOperacional> estados,
            long aposId, Pageable pagina) {
        if (estados.isEmpty()) return List.of();
        return consultarCandidatas(estados, caixa.minLat(), caixa.maxLat(), caixa.minLng(), caixa.maxLng(), aposId, pagina);
    }
}
