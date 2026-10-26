package br.com.powergrid.controlcenter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medidor_inteligente")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class MedidorInteligente extends AtivoRede {

    @Column(name = "numero_serie", nullable = false, unique = true, length = 128)
    private String numeroSerie;

    @Column(name = "endereco_fornecimento", columnDefinition = "TEXT")
    private String enderecoFornecimento;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "consumidor_id", nullable = true)
    private Consumidor consumidor;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "transformador_id", nullable = true)
    private Transformador transformador;
}
