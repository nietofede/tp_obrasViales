package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.ReprogramarFaseRequest;
import com.obrasviales.ms_obras.service.FaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/obras/fases")
@RequiredArgsConstructor
public class FaseController {

    private final FaseService faseService;

    @PostMapping("/{id}/iniciar")
    @PreAuthorize("hasAnyRole('CAPATAZ', 'SISTEMA')")
    public FaseResponse iniciar(@PathVariable Long id) {
        return FaseResponse.desde(faseService.iniciarFase(id));
    }

    @PostMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'SISTEMA')")
    public FaseResponse reprogramar(@PathVariable Long id,
                                    @Valid @RequestBody ReprogramarFaseRequest request) {
        return FaseResponse.desde(faseService.reprogramarFase(id, request.nuevaFechaInicio()));
    }
}