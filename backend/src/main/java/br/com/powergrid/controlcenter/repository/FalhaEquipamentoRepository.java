package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.FalhaEquipamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;

public interface FalhaEquipamentoRepository extends JpaRepository<FalhaEquipamento, Long> {
    @EntityGraph(attributePaths = {"eventoOperacional", "eventoOperacional.ativo", "eventoOperacional.registradorUsuario",
            "eventoOperacional.responsavelUsuario", "eventoOperacional.equipeDesignada"})
    Optional<FalhaEquipamento> findDetalhadaByIdAndEventoOperacionalCategoria(Long id, Categoria categoria);
}
