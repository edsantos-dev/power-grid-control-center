package br.com.powergrid.controlcenter.repository;

import java.util.List;
import br.com.powergrid.controlcenter.model.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    @Query(value = """
            SELECT a.* FROM alerta a
            WHERE a.situacao IN ('ABERTO', 'RECONHECIDO')
            ORDER BY CASE a.criticidade
                WHEN 'CRITICA' THEN 1
                WHEN 'ALTA' THEN 2
                WHEN 'MEDIA' THEN 3
                WHEN 'BAIXA' THEN 4
                ELSE 5
            END,
            a.gerado_em DESC NULLS LAST,
            a.id DESC
            """, nativeQuery = true)
    List<Alerta> buscarAtivos();
}
