package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.security.Roles;
import com.obrasviales.ms_operaciones.dtos.HabilitacionDiariaRequest;
import com.obrasviales.ms_operaciones.dtos.ResultadoHabilitacionDTO;
import com.obrasviales.ms_operaciones.servicios.HabilitacionDiariaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operaciones/habilitaciones-diarias")
@RequiredArgsConstructor
@Tag(name = "Habilitación diaria", description = "Proceso que habilita las órdenes programadas de una fecha")
public class HabilitacionDiariaController {

    private final HabilitacionDiariaService habilitacionDiariaService;

    @PostMapping
    @PreAuthorize("hasAnyRole('" + Roles.PLANIFICADOR + "', '" + Roles.SISTEMA + "')")
    @Operation(summary = "Habilita las órdenes de una fecha (Obras, Recursos y clima). Una orden que falla no frena a las demás; es reejecutable")
    public ResponseEntity<List<ResultadoHabilitacionDTO>> habilitar(@Valid @RequestBody HabilitacionDiariaRequest request) {
        return ResponseEntity.ok(habilitacionDiariaService.habilitarDelDia(request.fecha()));
    }
}
