package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_recursos.dto.ReservaResponse;
import com.obrasviales.ms_recursos.service.ReservaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/recursos/internos/reservas")
@PreAuthorize("hasRole('" + Roles.SISTEMA + "')")
public class ReservaController {
    
    private final ReservaService reservaService;
    
    public ReservaController(ReservaService reservaService){
        this.reservaService = reservaService;
    }

    // DTO (record) para recibir el JSON del POST, con las validaciones de entrada
    public record SolicitudReservaRequest(
            @NotNull Long ordenTrabajoId,
            @NotBlank String tipoTrabajo,
            @NotNull LocalDate fecha,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double tramoLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double tramoLon
            
    ) {}

    // Sin try/catch: el handler global de commons convierte los errores en formato estándar
    // (400 si falla la validación, 422 con el código de la regla si se rechaza la reserva, 404 y 409 según el caso).
    @PostMapping
    public ResponseEntity<ReservaResponse> proponerYReservar(@Valid @RequestBody SolicitudReservaRequest req) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ReservaResponse.desde(
                reservaService.proponerYReservar(
                        req.ordenTrabajoId(),
                        req.tipoTrabajo(),
                        req.fecha(),
                        req.tramoLat(),
                        req.tramoLon())));
    }

    // Sin try/catch: el handler global de commons convierte RecursoNoEncontradoException en 404
    // y ConflictoEstadoException en 409, con el formato estándar de error.
    @PatchMapping("/{id}/confirmacion")
    public ResponseEntity<ReservaResponse> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(ReservaResponse.desde(reservaService.confirmarReserva(id)));
    }

    @PatchMapping("/{id}/liberacion")
    public ResponseEntity<ReservaResponse> liberar(@PathVariable Long id) {
        return ResponseEntity.ok(ReservaResponse.desde(reservaService.liberarReserva(id)));
    }

}
