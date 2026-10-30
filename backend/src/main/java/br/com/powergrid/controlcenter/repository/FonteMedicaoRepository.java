package br.com.powergrid.controlcenter.repository;

import java.util.List;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FonteMedicaoRepository extends JpaRepository<FonteMedicao, Long> {
    boolean existsByIdentificador(String identificador);
    List<FonteMedicao> findByAtivoIdOrderByIdAsc(Long ativoId);
}
