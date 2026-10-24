package br.com.powergrid.controlcenter.repository;

import java.util.Optional;

import br.com.powergrid.controlcenter.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

}
