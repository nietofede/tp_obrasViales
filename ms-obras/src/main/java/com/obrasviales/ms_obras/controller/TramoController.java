package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.CrearFaseRequest;
import com.obrasviales.ms_obras.dto.CrearTramoRequest;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
import com.obrasviales.ms_obras.service.CronogramaService;
import com.obrasviales.ms_obras.service.FaseService;
import com.obrasviales.ms_obras.service.ObraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/obras/tramos")
@RequiredArgsConstructor
public class TramoController {

    private final FaseService faseService;
    private final CronogramaService cronogramaService;
    private final ObraService obraService;

    @GetMapping("/{tramoId}")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public TramoResponse obtener(@PathVariable Long tramoId) {
        return TramoResponse.desde(obraService.obtenerTramo(tramoId));
    }

    @PutMapping("/{tramoId}")
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public TramoResponse actualizar(@PathVariable Long tramoId,
                                    @Valid @RequestBody CrearTramoRequest request) {
        return TramoResponse.desde(obraService.actualizarTramo(
                tramoId,
                request.nombre(),
                request.superficieM2(),
                request.latitud(),
                request.longitud()));
    }

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

    @GetMapping("/{tramoId}/fases")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public List<FaseResponse> cronograma(@PathVariable Long tramoId) {
        return cronogramaService.cronogramaDelTramo(tramoId);
    }
}