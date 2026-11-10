package com.obrasviales.ms_obras.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "fases")
@Getter @Setter @NoArgsConstructor
public class Fase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramo_id")
    private Tramo tramo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFase tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoFase estado = EstadoFase.PENDIENTE;

    @Column(name = "fecha_inicio_planificada", nullable = false)
    private LocalDate fechaInicioPlanificada;

    @Column(name = "fecha_fin_planificada", nullable = false)
    private LocalDate fechaFinPlanificada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fase_anterior_id")
    private Fase faseAnterior;
}