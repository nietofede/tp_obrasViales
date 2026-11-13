package com.obrasviales.ms_recursos.controller;

import com.obrasviales.ms_recursos.dto.ReservaResponse;
import com.obrasviales.ms_recursos.service.ReservaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Consulta de una reserva por id. Va aparte de ReservaController porque ese es solo de la cuenta SISTEMA y
// acá también leen quienes planifican, supervisan y auditan.
@RestController
@RequestMapping("/api/recursos/reservas")
public class ReservaConsultaController {

    private final ReservaService reservaService;

    public ReservaConsultaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.LECTURA_O_SISTEMA)
    public ReservaResponse obtener(@PathVariable Long id) {
        return ReservaResponse.desde(reservaService.obtener(id));
    }
}
