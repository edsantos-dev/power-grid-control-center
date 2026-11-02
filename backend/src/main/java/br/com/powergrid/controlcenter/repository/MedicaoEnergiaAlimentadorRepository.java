package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.MedicaoEnergiaAlimentador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicaoEnergiaAlimentadorRepository extends JpaRepository<MedicaoEnergiaAlimentador, Long> {
    @Query("select (count(m) > 0) from MedicaoEnergiaAlimentador m"
            + " where m.fonte.ativo.id = :alimentadorId"
            + " and :novoInicio < m.fimPeriodo and :novoFim > m.inicioPeriodo")
    boolean existeSobreposicaoPorAlimentador(@Param("alimentadorId") Long alimentadorId,
            @Param("novoInicio") OffsetDateTime novoInicio, @Param("novoFim") OffsetDateTime novoFim);
}
