package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// reserva que devuelve ms-recursos. 
// los datos de mezcla son null si el trabajo no lleva mezcla.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaDTO(
        Long id,
        Long ordenTrabajoId,
        String estado,
        LocalDate fechaReserva,
        LocalDateTime fechaVencimientoTtl,
        Long cuadrillaId,
        Long plantaId,
        Integer tiempoViajeCuadrillaMin,
        Integer tiempoViajeMezclaMin,
        LocalTime horaLlegadaCuadrilla,
        LocalTime horaSalidaCamion,
        String fuenteRuta) {
}
