package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Fase que llego al limite de suspensiones y espera la decision del PLANIFICADOR.
@Entity
@Table(name = "fases_en_riesgo")
@Getter
@Setter
@NoArgsConstructor
public class FaseEnRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Column(name = "orden_trabajo_id", nullable = false)
    private Long ordenTrabajoId;

    @Column(name = "resuelta", nullable = false)
    private boolean resuelta = false;

    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "creado", nullable = false, updatable = false)
    private LocalDateTime creado;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    @PrePersist
    protected void onCreate() {
        creado = LocalDateTime.now();
    }
}
