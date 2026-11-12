package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoRequest;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoResponse;
import com.obrasviales.ms_operaciones.dtos.FaseEnRiesgoDTO;
import com.obrasviales.ms_operaciones.servicios.FaseEnRiesgoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operaciones/fases-en-riesgo")
@RequiredArgsConstructor
@Tag(name = "Fases en riesgo (RN-12)", description = "Fases que llegaron al límite de suspensiones y esperan una decisión")
public class FaseEnRiesgoController {

    private final FaseEnRiesgoService faseEnRiesgoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.AUDITOR + "')")
    @Operation(summary = "Listar las fases en riesgo pendientes de decisión")
    public ResponseEntity<List<FaseEnRiesgoDTO>> listar() {
        return ResponseEntity.ok(faseEnRiesgoService.listarPendientes());
    }

    @PostMapping("/{faseId}/decisiones")
    @PreAuthorize("hasRole('" + Roles.PLANIFICADOR + "')")
    @Operation(summary = "Decidir si una fase en riesgo se reprograma (con nueva fecha de inicio) o se cancela (PLANIFICADOR)")
    public ResponseEntity<DecisionRiesgoResponse> decidir(@PathVariable Long faseId,
                                                          @Valid @RequestBody DecisionRiesgoRequest request) {
        return ResponseEntity.ok(faseEnRiesgoService.decidir(faseId, request));
    }
}
