package br.com.powergrid.controlcenter.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fonte_medicao")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FonteMedicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ativo_id", nullable = false)
    private AtivoRede ativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subestacao_referencia_id")
    private Subestacao subestacaoReferencia;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private OrigemMedicao origem;

    @Column(name = "monitorada_desde", nullable = false)
    private OffsetDateTime monitoradaDesde;

    @Column(name = "ultima_comunicacao_em")
    private OffsetDateTime ultimaComunicacaoEm;

    @PrePersist
    private void iniciarMonitoramento() {
        if (monitoradaDesde == null) {
            monitoradaDesde = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        }
    }
}
