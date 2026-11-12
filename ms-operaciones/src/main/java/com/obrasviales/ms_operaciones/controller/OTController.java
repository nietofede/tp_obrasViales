package com.obrasviales.ms_operaciones.controller;

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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// avisa a Spring que esta clase expone endpoints REST y que las respuestas se convierten a JSON 
@RestController
// define las URL base para todos los endpoint de acá 
@RequestMapping("/api/v1/ordenes-trabajo")
// inyecta el service mediante el constructor
@RequiredArgsConstructor
// tag creo que era para que aparezca en swagger, tipo con nombre y descripcion
@Tag(name = "Órdenes de Trabajo", description = "Endpoints para la gestión del ciclo de vida de órdenes diarias")
public class OTController {

    private final OTServiceInterfaz otService;


    @PostMapping
    // operacion describe en swagger que hace el endpoint
    @Operation(summary = "Crear una nueva orden de trabajo en estado PROGRAMADA (PLANIFICADOR)")
    public ResponseEntity<OrdenTrabajoDTO> crearOrden(
            // valid hace las verificaciones del dto
            // requestbody convienrte a json
            @Valid @RequestBody CrearOrdenRequest request) {
        
        OrdenTrabajoDTO creada = otService.crearOrden(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada); //retorno 201
    }

    // endpoint de buscar por id
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una orden de trabajo por su ID")
    public ResponseEntity<OrdenTrabajoDTO> obtenerPorId(
            // con pathvariable obtenemos el id de la url y se lo mandamos al obtenerid del service
            @PathVariable Long id) {
        
        OrdenTrabajoDTO dto = otService.obtenerPorId(id);
        return ResponseEntity.ok(dto); // Devuelve 200 y json de la orden
    }

    // get ordenes
    @GetMapping
    @Operation(summary = "Listar órdenes de trabajo filtrando por fecha y/o estado")
    public ResponseEntity<List<OrdenTrabajoDTO>> listar(
            // @RequestParam(required = false): Permite que los parámetros de búsqueda en la URL sean opcionales.
            // @DateTimeFormat: parsear la fecha a localdate por las dudas 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoOrden estado) {
        
        List<OrdenTrabajoDTO> lista = otService.listarPorFechaYEstado(fecha, estado);
        return ResponseEntity.ok(lista); // devuelve 200 con las ordenes
    }

    // cambiar estado
    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado de una orden aplicando las validaciones de la RN-11")
    public ResponseEntity<OrdenTrabajoDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam EstadoOrden nuevoEstado) {
        
        // con el service vemos si podemos cambiar de estado 
        OrdenTrabajoDTO actualizada = otService.cambiarEstado(id, nuevoEstado);
        return ResponseEntity.ok(actualizada); // devuelvo 200 con la ot
    }

    // endpoint de evaluar clima y cambiar el estado 
    @PostMapping("/{id}/evaluar-clima")
    @Operation(summary = "Ejecuta la evaluación climática para una orden y actualiza su estado (HABILITADA, SUSPENDIDA o PENDIENTE)")
    public ResponseEntity<OrdenTrabajoDTO> evaluarClima(
            @PathVariable Long id,
            @RequestParam TipoTrabajo tipoTrabajo,
            @Valid @RequestBody EvaluacionSolicitudRequest request) {
        
        // Cambiá ordenTrabajoService por otService
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