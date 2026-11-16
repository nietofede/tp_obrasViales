package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.CrearFaseRequest;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.service.FaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/obras/tramos")
@RequiredArgsConstructor
public class TramoController {

    private final FaseService faseService;

    @PostMapping("/{tramoId}/fases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public FaseResponse crearFase(@PathVariable Long tramoId,
                                  @Valid @RequestBody CrearFaseRequest request) {
        return FaseResponse.desde(faseService.crearFase(
                tramoId,
                request.tipo(),
                request.fechaInicioPlanificada(),
                request.fechaFinPlanificada(),
                request.faseAnteriorId()));
    }

}