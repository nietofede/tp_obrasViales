package com.obrasviales.ms_recursos.controller;

import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioResponse;
import com.obrasviales.ms_recursos.service.CatalogoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Consulta de máquinas y operarios. Sin lógica: delega en el service.
@RestController
@RequestMapping("/api/recursos")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/maquinas")
    @PreAuthorize(Permisos.LECTURA)
    public List<MaquinaResponse> listarMaquinas() {
        return catalogoService.listarMaquinas();
    }

    @GetMapping("/operarios")
    @PreAuthorize(Permisos.LECTURA)
    public List<OperarioResponse> listarOperarios() {
        return catalogoService.listarOperarios();
    }
}
