package com.obrasviales.ms_recursos.controller;

import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.UbicacionRequest;
import com.obrasviales.ms_recursos.service.CuadrillaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recursos/cuadrillas")
public class CuadrillaController {

    private final CuadrillaService cuadrillaService;

    public CuadrillaController(CuadrillaService cuadrillaService) {
        this.cuadrillaService = cuadrillaService;
    }

    @PostMapping
    @PreAuthorize(Permisos.SOLO_PLANIFICADOR)
    public ResponseEntity<CuadrillaResponse> crear(@Valid @RequestBody CuadrillaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuadrillaService.crear(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.SOLO_PLANIFICADOR)
    public CuadrillaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CuadrillaRequest req) {
        return cuadrillaService.actualizar(id, req);
    }

    @GetMapping
    @PreAuthorize(Permisos.LECTURA)
    public List<CuadrillaResponse> listar() {
        return cuadrillaService.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.LECTURA)
    public CuadrillaResponse obtener(@PathVariable Long id) {
        return cuadrillaService.obtener(id);
    }

    // Check-in de ubicación del CAPATAZ de la cuadrilla.
    @PostMapping("/{id}/ubicaciones")
    @PreAuthorize(Permisos.SOLO_CAPATAZ)
    public CuadrillaResponse registrarUbicacion(@PathVariable Long id,
                                                @Valid @RequestBody UbicacionRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        return cuadrillaService.registrarUbicacion(id, req, jwt.getClaimAsString("preferred_username"));
    }
}
