package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Cada vez que una orden se suspende queda un registro. RN-12 cuenta las suspensiones vigentes de la FASE.
@Entity
@Table(name = "suspensiones")
@Getter
@Setter
@NoArgsConstructor
public class Suspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_trabajo_id", nullable = false)
    private Long ordenTrabajoId;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false, length = 30)
    private MotivoSuspension motivo;

    @Column(name = "detalle", length = 500)
    private String detalle;

    @Column(name = "vigente", nullable = false)
    private boolean vigente = true;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onCreate() {
        fecha = LocalDateTime.now();
    }
}
