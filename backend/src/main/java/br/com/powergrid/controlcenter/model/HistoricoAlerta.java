package br.com.powergrid.controlcenter.model;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "historico_alerta", uniqueConstraints = @UniqueConstraint(
        name = "uq_historico_alerta_destino", columnNames = {"alerta_id", "situacao_nova"}))
@Immutable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HistoricoAlerta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alerta_id", nullable = false, updatable = false)
    private Alerta alerta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "situacao_anterior", nullable = false, length = 32, updatable = false)
    private SituacaoAlerta situacaoAnterior;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "situacao_nova", nullable = false, length = 32, updatable = false)
    private SituacaoAlerta situacaoNova;

    @Column(name = "alterado_em", nullable = false, updatable = false)
    private OffsetDateTime alteradoEm;

    @Column(nullable = false, length = 1000, updatable = false)
    private String comentario;
}
