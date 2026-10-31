package br.com.powergrid.controlcenter.model;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alerta_comunicacao")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertaComunicacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Criticidade criticidade = Criticidade.MEDIA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fonte_id", nullable = false)
    private FonteMedicao fonte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ativo_id", nullable = false)
    private AtivoRede ativo;

    @Column(name = "monitorada_desde", nullable = false)
    private OffsetDateTime monitoradaDesde;

    @Column(name = "ultima_comunicacao_em")
    private OffsetDateTime ultimaComunicacaoEm;

    @Column(name = "tolerancia_segundos", nullable = false)
    private Integer toleranciaSegundos;

    @Column(name = "detectado_em", nullable = false)
    private OffsetDateTime detectadoEm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StatusComunicacao status;

    @Column(name = "restabelecido_em")
    private OffsetDateTime restabelecidoEm;
}
