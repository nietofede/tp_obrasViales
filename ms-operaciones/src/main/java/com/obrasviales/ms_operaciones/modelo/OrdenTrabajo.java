package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ordenes_trabajo")
@Getter
@Setter
@NoArgsConstructor
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Column(name = "tramo_id", nullable = false)
    private Long tramoId;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 40)
    private EstadoOrden estado;

    @Column(name = "reserva_id")
    private Long reservaId;

    @Column(name = "hora_salida_camion")
    private LocalDateTime horaSalidaCamion;

    @Column(name = "contador_suspensiones", nullable = false)
    private Integer contadorSuspensiones = 0;

    @Version
    private Long version;

    @Column(name = "creado", nullable = false, updatable = false)
    private LocalDateTime creado;

    @Column(name = "actualizado", nullable = false)
    private LocalDateTime actualizado;

    @PrePersist
    protected void onCreate() {
        creado = LocalDateTime.now();
        actualizado = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizado = LocalDateTime.now();
    }
}