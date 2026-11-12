package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.UmbralClimaticoRequest;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.modelo.UmbralClimatico;
import com.obrasviales.ms_operaciones.servicios.UmbralClimaticoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// lo separe por las dudas, es para la RN 09 de que solo el admin configura los umbrales 
@RestController
@RequestMapping("/api/operaciones/umbrales-climaticos")
@RequiredArgsConstructor
@Tag(name = "Configuración Climática (RN-09)", description = "Gestión de límites meteorológicos por tipo de trabajo")
public class UmbralClimaticoController {

    private final UmbralClimaticoService umbralService;

    @PutMapping("/{tipoTrabajo}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')") 
    @Operation(summary = "Configurar o actualizar los umbrales climáticos (Solo ADMIN)")
    public ResponseEntity<UmbralClimatico> actualizarUmbral(
            @PathVariable TipoTrabajo tipoTrabajo,
            @Valid @RequestBody UmbralClimaticoRequest request) {
        
        UmbralClimatico actualizado = umbralService.guardarOActualizarUmbral(tipoTrabajo, request);
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.PLANIFICADOR + "', '" + Roles.CAPATAZ + "', '" + Roles.AUDITOR + "')")
    @Operation(summary = "Consultar los umbrales climáticos vigentes de todos los tipos de trabajo")
    public ResponseEntity<List<UmbralClimatico>> listarUmbrales() {
        return ResponseEntity.ok(umbralService.listarUmbrales());
    }
}
