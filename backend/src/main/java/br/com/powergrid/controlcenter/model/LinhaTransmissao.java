package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
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
@Table(name = "linha_transmissao")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class LinhaTransmissao extends AtivoRede {

    @Column(name = "extensao_km", precision = 19, scale = 6)
    private BigDecimal extensaoKm;

    @Column(name = "tensao_operacao_kv", precision = 19, scale = 6)
    private BigDecimal tensaoOperacaoKv;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subestacao_origem_id", nullable = false)
    private Subestacao subestacaoOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subestacao_destino_id", nullable = false)
    private Subestacao subestacaoDestino;
}
