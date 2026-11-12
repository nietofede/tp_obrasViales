package com.obrasviales.ms_obras.controller;

import com.obrasviales.ms_obras.dto.CrearObraRequest;
import com.obrasviales.ms_obras.dto.CrearTramoRequest;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.ObraDetalleResponse;
import com.obrasviales.ms_obras.dto.ObraResponse;
import com.obrasviales.ms_obras.dto.ObraResumenResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public List<ObraResumenResponse> listar() {
        return obraService.listarObras();
    }

    @GetMapping("/{obraId}")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public ObraDetalleResponse obtener(@PathVariable Long obraId) {
        return obraService.obtenerObra(obraId);
    }

    @PutMapping("/{obraId}")
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public ObraResponse actualizar(@PathVariable Long obraId,
                                   @Valid @RequestBody CrearObraRequest request) {
        return ObraResponse.desde(
                obraService.actualizarObra(obraId, request.nombre(), request.descripcion()));
    }

    @PostMapping("/{obraId}/tramos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public TramoResponse crearTramo(@PathVariable Long obraId,
                                    @Valid @RequestBody CrearTramoRequest request) {
        return TramoResponse.desde(obraService.crearTramo(
                obraId,
                request.nombre(),
                request.superficieM2(),
                request.latitud(),
                request.longitud()));
    }

    @GetMapping("/{obraId}/tramos")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public List<TramoResponse> tramos(@PathVariable Long obraId) {
        return obraService.listarTramos(obraId);
    }

    @GetMapping("/{obraId}/cronograma")
    @PreAuthorize("hasAnyRole('PLANIFICADOR', 'CAPATAZ', 'AUDITOR')")
    public List<FaseResponse> cronograma(@PathVariable Long obraId) {
        return obraService.cronogramaDeObra(obraId);
    }
}