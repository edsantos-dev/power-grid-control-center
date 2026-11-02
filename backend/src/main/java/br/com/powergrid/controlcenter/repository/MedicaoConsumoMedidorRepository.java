package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.MedicaoConsumoMedidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicaoConsumoMedidorRepository extends JpaRepository<MedicaoConsumoMedidor, Long> {
    @Query("select (count(m) > 0) from MedicaoConsumoMedidor m"
            + " where m.fonte.ativo.id = :medidorId"
            + " and :novoInicio < m.fimPeriodo and :novoFim > m.inicioPeriodo")
    boolean existeSobreposicaoPorMedidor(@Param("medidorId") Long medidorId,
            @Param("novoInicio") OffsetDateTime novoInicio, @Param("novoFim") OffsetDateTime novoFim);
}
