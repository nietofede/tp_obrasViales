package com.obrasviales.ms_recursos.controller;

import com.obrasviales.ms_recursos.dto.CatalogoDtos.CertificacionRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CertificacionResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.PlantaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.PlantaResponse;
import com.obrasviales.ms_recursos.dto.ReservaResponse;
import com.obrasviales.ms_recursos.service.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Catálogos de máquinas, operarios, certificaciones y plantas. Sin lógica: valida la entrada, delega en el
// service y deja que el handler global de commons convierta las excepciones en errores estándar.
@RestController
@RequestMapping("/api/recursos")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    // ---------- Máquinas ----------

    @PostMapping("/maquinas")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public ResponseEntity<MaquinaResponse> crearMaquina(@Valid @RequestBody MaquinaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoService.crearMaquina(req));
    }

    @PutMapping("/maquinas/{id}")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public MaquinaResponse actualizarMaquina(@PathVariable Long id, @Valid @RequestBody MaquinaRequest req) {
        return catalogoService.actualizarMaquina(id, req);
    }

    @GetMapping("/maquinas")
    @PreAuthorize(Permisos.LECTURA)
    public List<MaquinaResponse> listarMaquinas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return catalogoService.listarMaquinas(fecha);
    }

    // ---------- Operarios y certificaciones ----------

    @PostMapping("/operarios")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public ResponseEntity<OperarioResponse> crearOperario(@Valid @RequestBody OperarioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoService.crearOperario(req));
    }

    @GetMapping("/operarios")
    @PreAuthorize(Permisos.LECTURA)
    public List<OperarioResponse> listarOperarios() {
        return catalogoService.listarOperarios();
    }

    @PostMapping("/operarios/{id}/certificaciones")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public ResponseEntity<CertificacionResponse> agregarCertificacion(
            @PathVariable Long id, @Valid @RequestBody CertificacionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoService.agregarCertificacion(id, req));
    }

    // El operario ve a qué trabajos fue asignado su cuadrilla. El usuario sale del token, no de la URL.
    @GetMapping("/operarios/me/asignaciones")
    @PreAuthorize(Permisos.SOLO_OPERARIO)
    public List<ReservaResponse> misAsignaciones(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return catalogoService.asignacionesDe(jwt.getClaimAsString("preferred_username"), fecha);
    }

    // ---------- Plantas de mezcla ----------

    @PostMapping("/plantas")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public ResponseEntity<PlantaResponse> crearPlanta(@Valid @RequestBody PlantaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoService.crearPlanta(req));
    }

    @GetMapping("/plantas")
    @PreAuthorize(Permisos.ADMIN_O_PLANIFICADOR)
    public List<PlantaResponse> listarPlantas() {
        return catalogoService.listarPlantas();
    }
}
