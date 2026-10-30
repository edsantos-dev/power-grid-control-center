package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.FonteMedicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FonteMedicaoRepository extends JpaRepository<FonteMedicao, Long> {
    boolean existsByIdentificador(String identificador);
}
