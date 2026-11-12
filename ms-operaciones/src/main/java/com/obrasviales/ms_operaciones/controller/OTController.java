package com.obrasviales.ms_operaciones.controller;
import com.obrasviales.ms_operaciones.dtos.EvaluacionClimaticaResponseDTO;
import com.obrasviales.commons.security.Roles; // <--- Importamos los roles del commons de tu amigo
import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ReprogramacionOrdenRequest;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.servicios.OTServiceInterfaz;
import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // <--- Importante para la seguridad
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/operaciones/ordenes")
@RequiredArgsConstructor
@Tag(name = "Órdenes de Trabajo", description = "Endpoints para la gestión del ciclo de vida de órdenes diarias")
public class OTController {

    private final OTServiceInterfaz otService;

    @PostMapping
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.ADMIN + "')") 
    @Operation(summary = "Crear una nueva orden de trabajo en estado PROGRAMADA (PLANIFICADOR)")
    public ResponseEntity<OrdenTrabajoDTO> crearOrden(
            @Valid @RequestBody CrearOrdenRequest request) {
        
        OrdenTrabajoDTO creada = otService.crearOrden(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // Cualquier usuario logueado puede consultar
    @Operation(summary = "Obtener una orden de trabajo por su ID")
    public ResponseEntity<OrdenTrabajoDTO> obtenerPorId(@PathVariable Long id) {
        OrdenTrabajoDTO dto = otService.obtenerPorId(id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar órdenes de trabajo filtrando por fecha y/o estado")
    public ResponseEntity<List<OrdenTrabajoDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoOrden estado) {
        
        List<OrdenTrabajoDTO> lista = otService.listarPorFechaYEstado(fecha, estado);
        return ResponseEntity.ok(lista);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('" + Roles.CAPATAZ + "', '" + Roles.PLANIFICADOR + "')") 
    @Operation(summary = "Cambiar el estado de una orden aplicando las validaciones de la RN-11")
    public ResponseEntity<OrdenTrabajoDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam EstadoOrden nuevoEstado) {
        
        OrdenTrabajoDTO actualizada = otService.cambiarEstado(id, nuevoEstado);
        return ResponseEntity.ok(actualizada);
    }

    @PostMapping("/{id}/habilitacion")
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.SISTEMA + "')")
    @Operation(summary = "Evalúa la habilitación de una orden: consulta la fase a Obras (RN-01) y decide por clima (HABILITADA, SUSPENDIDA o PENDIENTE_CONFIRMACION_CLIMATICA)")
    public ResponseEntity<OrdenTrabajoDTO> habilitar(@PathVariable Long id) {
        return ResponseEntity.ok(otService.habilitarOrden(id));
    }

    @GetMapping("/{id}/evaluaciones-climaticas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener el historial de evaluaciones climáticas de una orden")
    public ResponseEntity<List<EvaluacionClimaticaResponseDTO>> obtenerHistorialClimatico(@PathVariable Long id) {
        List<EvaluacionClimaticaResponseDTO> historial = otService.obtenerHistorialClimatico(id)
                .stream()
                .map(EvaluacionClimaticaResponseDTO::desde)
                .toList();
        return ResponseEntity.ok(historial);
    }

    @PatchMapping("/{id}/cancelacion")
    @PreAuthorize("hasRole('" + Roles.PLANIFICADOR + "')")
    @Operation(summary = "Cancelar una orden y liberar sus reservas (PLANIFICADOR)")
    public ResponseEntity<OrdenTrabajoDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(otService.cancelarOrden(id));
    }

    @PatchMapping("/{id}/reprogramacion")
    @PreAuthorize("hasRole('" + Roles.PLANIFICADOR + "')")
    @Operation(summary = "Mover de fecha una orden PROGRAMADA sin recursos, para reintentar su reserva (PLANIFICADOR)")
    public ResponseEntity<OrdenTrabajoDTO> reprogramar(@PathVariable Long id,
                                                       @Valid @RequestBody ReprogramacionOrdenRequest request) {
        return ResponseEntity.ok(otService.reprogramarOrden(id, request.fecha()));
    }
}
