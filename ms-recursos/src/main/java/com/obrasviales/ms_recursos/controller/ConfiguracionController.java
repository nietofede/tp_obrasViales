package com.obrasviales.ms_recursos.controller;

import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionResponse;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoResponse;
import com.obrasviales.ms_recursos.service.ConfiguracionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Configuración del motor de elegibilidad: la modifica el ADMIN, la consultan quienes planifican y auditan.
@RestController
@RequestMapping("/api/recursos")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping("/tipos-trabajo/composicion-minima")
    @PreAuthorize(Permisos.LECTURA)
    public List<ComposicionResponse> listarComposiciones() {
        return configuracionService.listarComposiciones();
    }

    @PutMapping("/tipos-trabajo/{tipo}/composicion-minima")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public ComposicionResponse actualizarComposicion(@PathVariable String tipo,
                                                     @Valid @RequestBody ComposicionRequest req) {
        return configuracionService.actualizarComposicion(tipo, req);
    }

    @GetMapping("/configuracion/desplazamiento")
    @PreAuthorize(Permisos.ADMIN_O_PLANIFICADOR)
    public DesplazamientoResponse obtenerDesplazamiento() {
        return configuracionService.obtenerDesplazamiento();
    }

    @PutMapping("/configuracion/desplazamiento")
    @PreAuthorize(Permisos.SOLO_ADMIN)
    public DesplazamientoResponse actualizarDesplazamiento(@Valid @RequestBody DesplazamientoRequest req) {
        return configuracionService.actualizarDesplazamiento(req);
    }
}
