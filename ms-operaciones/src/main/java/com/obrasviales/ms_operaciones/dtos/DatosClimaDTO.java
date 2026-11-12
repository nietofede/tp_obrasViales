package com.obrasviales.ms_operaciones.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatosClimaDTO {
    private Double humedadSuelo;        // humedad volumetrica del suelo (m3/m3, 0 a 1)
    private Double probabilidadLluvia;  // % probabilidad de precipitación
    private Double temperatura;        // °C
}