package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.CrearObraRequest;
import com.obrasviales.ms_obras.dto.ObraResponse;
import com.obrasviales.ms_obras.service.ObraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/obras")
@RequiredArgsConstructor
public class ObraController {

    private final ObraService obraService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public ObraResponse crear(@Valid @RequestBody CrearObraRequest request) {
        return ObraResponse.desde(obraService.crearObra(request.nombre(), request.descripcion()));
    }
}