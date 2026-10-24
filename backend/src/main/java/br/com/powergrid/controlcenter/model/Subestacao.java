package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subestacao")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Subestacao extends AtivoRede {

	@Column(name = "tensao_nominal_kv", precision = 19, scale = 6)
	private BigDecimal tensaoNominalKv;

	@Column(name = "capacidade_mva", precision = 19, scale = 6)
	private BigDecimal capacidadeMva;

}
