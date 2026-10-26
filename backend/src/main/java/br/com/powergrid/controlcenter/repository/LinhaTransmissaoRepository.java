package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.LinhaTransmissao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinhaTransmissaoRepository extends JpaRepository<LinhaTransmissao, Long> {
}
