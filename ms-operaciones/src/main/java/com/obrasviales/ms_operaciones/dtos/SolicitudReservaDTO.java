package com.obrasviales.ms_operaciones.dtos;

// Cuerpo de POST /api/recursos/internos/reservas
// la fecha va como texto despues se parsea
public record SolicitudReservaDTO(
        Long ordenTrabajoId,
        String tipoTrabajo,
        String fecha,
        Double tramoLat,
        Double tramoLon) {
}
