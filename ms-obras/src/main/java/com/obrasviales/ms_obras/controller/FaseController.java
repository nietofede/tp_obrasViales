package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.dto.ReprogramarFaseRequest;
import com.obrasviales.ms_obras.service.FaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/obras/fases")
@RequiredArgsConstructor
public class FaseController {

    private final FaseService faseService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public FaseResponse obtener(@PathVariable Long id) {
        return FaseResponse.desde(faseService.obtenerFase(id));
    }

    @GetMapping("/{id}/materiales")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ')")
    public List<MaterialCalculado> materiales(@PathVariable Long id) {
        return faseService.materialesDeFase(id);
    }

    @PostMapping("/{id}/iniciar")
    @PreAuthorize("hasAnyRole('CAPATAZ', 'SISTEMA')")
    public FaseResponse iniciar(@PathVariable Long id) {
        return FaseResponse.desde(faseService.iniciarFase(id));
    }

    @PatchMapping("/{id}/programacion")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'SISTEMA')")
    public FaseResponse reprogramar(@PathVariable Long id,
                                    @Valid @RequestBody ReprogramarFaseRequest request) {
        return FaseResponse.desde(faseService.reprogramarFase(id, request.nuevaFechaInicio()));
    }
}