package br.com.powergrid.controlcenter.model;

import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "nome", length = 255)
	private String nome;

	@Column(name = "matricula", nullable = false, unique = true, length = 64)
	private String matricula;

	@Column(name = "email", nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "senha_hash", length = 255)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "perfil", nullable = false, length = 32)
	private PerfilAcesso perfil;

	@Column(name = "cargo_profissional", length = 255)
	private String cargoProfissional;

	@PrePersist
	@PreUpdate
	private void normalizarEmail() {
		email = NormalizadorEmail.normalizar(email);
	}

}
