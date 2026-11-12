package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.ms_operaciones.dtos.ReservaDTO;

import java.time.LocalDate;

// Lo que ms-operaciones necesita de ms-recursos
public interface RecursosClient {

    // "Proponer y reservar" devuelve una reserva PROVISORIA con vencimiento
    ReservaDTO proponerYReservar(Long ordenTrabajoId, String tipoTrabajo, LocalDate fecha, Double tramoLat, Double tramoLon);

    // Confirma una reserva provisoria 
    ReservaDTO confirmar(Long reservaId);

    // Libera una reserva 
    ReservaDTO liberar(Long reservaId);
}
