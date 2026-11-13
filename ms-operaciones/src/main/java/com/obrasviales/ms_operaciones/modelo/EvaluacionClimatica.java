package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluaciones_climaticas")
@Getter
@Setter
@NoArgsConstructor
public class EvaluacionClimatica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    @JsonIgnore
    private OrdenTrabajo ordenTrabajo;

    @Column(name = "humedad_suelo_registrada")
    private Double humedadSueloRegistrada;

    @Column(name = "prob_precipitacion_registrada")
    private Double probPrecipitacionRegistrada;

    @Column(name = "temperatura_registrada")
    private Double temperaturaRegistrada;

    @Column(name = "umbrales_cumplidos", nullable = false)
    private Boolean umbralesCumplidos;

    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDateTime fechaEvaluacion;

    @PrePersist
    protected void onCreate() {
        fechaEvaluacion = LocalDateTime.now();
    }
}
