package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.AvanceOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.AvanceOrdenResponse;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.RechazoOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.ResolucionClimaticaRequest;
import com.obrasviales.ms_operaciones.servicios.EjecucionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operaciones/ordenes")
@RequiredArgsConstructor
@Tag(name = "Ejecución de órdenes", description = "Decisiones del capataz: resolución climática, inicio, rechazo y avances")
public class EjecucionController {

    private final EjecucionService ejecucionService;

    @PatchMapping("/{id}/resolucion-climatica")
    @PreAuthorize("hasRole('" + Roles.CAPATAZ + "')")
    @Operation(summary = "Resolver una orden PENDIENTE_CONFIRMACION_CLIMATICA: habilitarla o suspenderla (RN-10, CAPATAZ)")
    public ResponseEntity<OrdenTrabajoDTO> resolverClima(@PathVariable Long id,
                                                         @Valid @RequestBody ResolucionClimaticaRequest request) {
        return ResponseEntity.ok(ejecucionService.resolverClima(id, request));
    }

    @PatchMapping("/{id}/inicio")
    @PreAuthorize("hasRole('" + Roles.CAPATAZ + "')")
    @Operation(summary = "Iniciar la ejecución de una orden HABILITADA (CAPATAZ)")
    public ResponseEntity<OrdenTrabajoDTO> iniciar(@PathVariable Long id) {
        return ResponseEntity.ok(ejecucionService.iniciar(id));
    }

    @PatchMapping("/{id}/rechazo")
    @PreAuthorize("hasRole('" + Roles.CAPATAZ + "')")
    @Operation(summary = "Rechazar una orden HABILITADA y suspenderla con motivo; dispara la saga de suspensión (CAPATAZ)")
    public ResponseEntity<OrdenTrabajoDTO> rechazar(@PathVariable Long id, @Valid @RequestBody RechazoOrdenRequest request) {
        return ResponseEntity.ok(ejecucionService.rechazar(id, request.motivo()));
    }

    @PostMapping("/{id}/avances")
    @PreAuthorize("hasRole('" + Roles.CAPATAZ + "')")
    @Operation(summary = "Registrar el avance de ejecución (porcentaje acumulado de la fase) en una orden EN_EJECUCION (CAPATAZ)")
    public ResponseEntity<AvanceOrdenResponse> registrarAvance(@PathVariable Long id,
                                                               @Valid @RequestBody AvanceOrdenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ejecucionService.registrarAvance(id, request.porcentaje()));
    }
}
