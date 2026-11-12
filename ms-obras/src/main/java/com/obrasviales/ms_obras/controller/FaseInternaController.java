package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.RegistrarAvanceRequest;
import com.obrasviales.ms_obras.service.FaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Endpoints internos: los usa ms-operaciones con la cuenta SISTEMA
@RestController
@RequestMapping("/api/obras/internos/fases")
@PreAuthorize("hasRole('SISTEMA')")
@RequiredArgsConstructor
public class FaseInternaController {

    private final FaseService faseService;

    @PostMapping("/{id}/avances")
    @ResponseStatus(HttpStatus.CREATED)
    public FaseResponse registrarAvance(@PathVariable Long id,
                                        @Valid @RequestBody RegistrarAvanceRequest request) {
        return FaseResponse.desde(faseService.registrarAvance(id, request.porcentaje()));
    }

    @PatchMapping("/{id}/riesgo")
    public FaseResponse marcarEnRiesgo(@PathVariable Long id) {
        return FaseResponse.desde(faseService.marcarEnRiesgo(id));
    }
}