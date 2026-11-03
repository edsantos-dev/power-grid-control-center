package br.com.powergrid.controlcenter.model;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "historico_designacao_equipe", uniqueConstraints = @UniqueConstraint(
        name = "uq_designacao_evento_versao", columnNames = {"evento_operacional_id", "versao_designacao"}))
@Immutable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HistoricoDesignacaoEquipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_operacional_id", nullable = false, updatable = false)
    private EventoOperacional eventoOperacional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_anterior_id", updatable = false)
    private EquipeManutencao equipeAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_nova_id", updatable = false)
    private EquipeManutencao equipeNova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private OffsetDateTime registradoEm;

    @Column(nullable = false, length = 1000, updatable = false)
    private String comentario;

    @Column(name = "versao_designacao", nullable = false, updatable = false)
    private Integer versaoDesignacao;
}
