package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_recursos.entity.Reserva;
import com.obrasviales.ms_recursos.service.ReservaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/recursos/internos/reservas")
@PreAuthorize("hasRole('" + Roles.SISTEMA + "')")
public class ReservaController {
    
    private final ReservaService reservaService;
    
    public ReservaController(ReservaService reservaService){
        this.reservaService = reservaService;
    }

    // DTO (record) para recibir el JSON del POST
    public record SolicitudReservaRequest(
            Long ordenTrabajoId,
            String tipoTrabajo,
            LocalDate fecha,
            double tramoLat,
            double tramoLon
    ) {}

    @PostMapping
    public ResponseEntity<?> proponerYReservar(@RequestBody SolicitudReservaRequest req) {
        try {
            Reserva reserva = reservaService.proponerYReservar(
                req.ordenTrabajoId(),
                req.tipoTrabajo(),
                req.fecha(),
                req.tramoLat(),
                req.tramoLon()
            );
            
            // Armamos la respuesta con los datos clave sin arrastrar las listas circulares
            Map<String, Object> response = Map.of(
                "id", reserva.getId(),
                "ordenTrabajoId", reserva.getOrdenTrabajoId(),
                "estado", reserva.getEstado(),
                "fechaReserva", reserva.getFechaReserva().toString(),
                "fechaVencimientoTtl", reserva.getFechaVencimientoTtl().toString(),
                "tiempoViajeCuadrillaMin", reserva.getTiempoViajeCuadrillaMin(),
                "horaLlegadaCuadrilla", reserva.getHoraLlegadaCuadrilla() != null ? reserva.getHoraLlegadaCuadrilla().toString() : "",
                "fuenteRuta", reserva.getFuenteRuta(),
                "cuadrillaId", reserva.getCuadrilla() != null ? reserva.getCuadrilla().getId() : null
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException | IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmar(@PathVariable Long id) {
        try {
            Reserva reserva = reservaService.confirmarReserva(id);
            return ResponseEntity.ok(Map.of(
                "id", reserva.getId(),
                "ordenTrabajoId", reserva.getOrdenTrabajoId(),
                "estado", reserva.getEstado(),
                "fechaReserva", reserva.getFechaReserva().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberar(@PathVariable Long id) {
        try {
            Reserva reserva = reservaService.liberarReserva(id);
            return ResponseEntity.ok(Map.of(
                "id", reserva.getId(),
                "ordenTrabajoId", reserva.getOrdenTrabajoId(),
                "estado", reserva.getEstado()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}
