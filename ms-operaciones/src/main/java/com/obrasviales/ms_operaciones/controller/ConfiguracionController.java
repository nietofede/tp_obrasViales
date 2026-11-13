package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.LimiteSuspensionesDTO;
import com.obrasviales.ms_operaciones.servicios.SuspensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// controller para la cantidad de suspensiones que llevan una fase a estar en riesgo 
@RestController
@RequestMapping("/api/operaciones/configuracion")
@RequiredArgsConstructor
@Tag(name = "Configuración de suspensiones (RN-12)", description = "Cuántas suspensiones llevan una fase a estar en riesgo")
public class ConfiguracionController {

    private final SuspensionService suspensionService;

    @PutMapping("/limite-suspensiones")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Operation(summary = "Definir cuántas suspensiones llevan una fase a estar en riesgo (ADMIN)")
    public ResponseEntity<LimiteSuspensionesDTO> actualizarLimite(@Valid @RequestBody LimiteSuspensionesDTO request) {
        return ResponseEntity.ok(new LimiteSuspensionesDTO(suspensionService.actualizarLimite(request.limite())));
    }
}
