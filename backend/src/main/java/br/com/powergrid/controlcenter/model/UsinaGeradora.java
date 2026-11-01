package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "usina_geradora")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class UsinaGeradora extends AtivoRede {
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_geracao", nullable = false, length = 100)
    private TipoGeracao tipoGeracao;

    @Column(name = "capacidade_maxima_mw", nullable = false, precision = 19, scale = 6)
    private BigDecimal capacidadeMaximaMw;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subestacao_id", nullable = false)
    private Subestacao subestacao;
}
