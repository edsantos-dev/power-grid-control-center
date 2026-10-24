package br.com.powergrid.controlcenter.repository;

import br.com.powergrid.controlcenter.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

}
