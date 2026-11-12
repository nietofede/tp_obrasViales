package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles; // <--- Importamos los roles del commons de tu amigo
import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.EvaluacionSolicitudRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
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
@RequestMapping("/api/v1/ordenes-trabajo")
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

    @PostMapping("/{id}/evaluar-clima")
    @PreAuthorize("hasRole('" + Roles.CAPATAZ + "')") 
    @Operation(summary = "Ejecuta la evaluación climática para una orden y actualiza su estado (HABILITADA, SUSPENDIDA o PENDIENTE)")
    public ResponseEntity<OrdenTrabajoDTO> evaluarClima(
            @PathVariable Long id,
            @RequestParam TipoTrabajo tipoTrabajo,
            @Valid @RequestBody EvaluacionSolicitudRequest request) {
        
        OrdenTrabajoDTO resultado = otService.evaluarYActualizarEstadoClimatico(
                id, request.getLatitud(), request.getLongitud(), tipoTrabajo);
        
        return ResponseEntity.ok(resultado);
    }

    // no consegui hacerlo andar, me tira error 500 y no da info, creo igual que es secundario, no obligatorio
    @GetMapping("/{id}/evaluaciones-climaticas")

    public ResponseEntity<List<EvaluacionClimatica>> obtenerHistorialClimatico(@PathVariable Long id) {

        List<EvaluacionClimatica> historial = otService.obtenerHistorialClimatico(id);

        return ResponseEntity.ok(historial);

    } 
}