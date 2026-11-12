package com.obrasviales.ms_recursos.dto;

import com.obrasviales.ms_recursos.entity.Reserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Contrato de salida de las reservas: lo que Operaciones necesita para seguir el proceso, en especial
// la hora de salida del camión. Los datos de mezcla son null si el trabajo no lleva mezcla.
public record ReservaResponse(
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

    public static ReservaResponse desde(Reserva r) {
        return new ReservaResponse(
                r.getId(),
                r.getOrdenTrabajoId(),
                r.getEstado(),
                r.getFechaReserva(),
                r.getFechaVencimientoTtl(),
                r.getCuadrilla() != null ? r.getCuadrilla().getId() : null,
                r.getPlanta() != null ? r.getPlanta().getId() : null,
                r.getTiempoViajeCuadrillaMin(),
                r.getTiempoViajeMezclaMin(),
                r.getHoraLlegadaCuadrilla(),
                r.getHoraSalidaCamion(),
                r.getFuenteRuta());
    }
}