package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.SuspensionDTO;
import com.obrasviales.ms_operaciones.servicios.SuspensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operaciones/suspensiones")
@RequiredArgsConstructor
@Tag(name = "Suspensiones", description = "Historial de suspensiones por fase u orden")
public class SuspensionController {

    private final SuspensionService suspensionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.AUDITOR + "')")
    @Operation(summary = "Consultar el historial de suspensiones, filtrando por fase u orden")
    public ResponseEntity<List<SuspensionDTO>> historial(@RequestParam(required = false) Long faseId,
                                                         @RequestParam(required = false) Long ordenId) {
        return ResponseEntity.ok(suspensionService.historial(faseId, ordenId));
    }
}
