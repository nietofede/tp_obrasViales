package com.obrasviales.ms_operaciones.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "umbrales_climaticos")
@Getter
@Setter
@NoArgsConstructor
public class UmbralClimatico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_trabajo", nullable = false, unique = true, length = 50)
    private TipoTrabajo tipoTrabajo;

    @Column(name = "humedad_suelo_maxima", nullable = false)
    private Double humedadSueloMaxima;

    @Column(name = "prob_precipitacion_maxima", nullable = false)
    private Double probPrecipitacionMaxima;

    @Column(name = "temperatura_minima", nullable = false)
    private Double temperaturaMinima;
}