package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.DatosOperacionResponse;
import com.obrasviales.ms_obras.service.OperacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/obras/internos/fases")
@RequiredArgsConstructor
public class OperacionController {

    private final OperacionService operacionService;

    @GetMapping("/{id}/contexto-ejecucion")
    @PreAuthorize("hasRole('SISTEMA')")
    public DatosOperacionResponse contextoEjecucion(@PathVariable Long id) {
        return operacionService.obtenerDatosOperacion(id);
    }
}