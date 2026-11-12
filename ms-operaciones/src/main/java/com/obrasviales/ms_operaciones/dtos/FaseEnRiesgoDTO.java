package com.obrasviales.ms_operaciones.dtos;

import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;

import java.time.LocalDateTime;

// Fase que llego al limite de suspensiones y espera la decision del PLANIFICADOR.
public record FaseEnRiesgoDTO(Long faseId, Long ordenTrabajoId, LocalDateTime enRiesgoDesde) {

    public static FaseEnRiesgoDTO desde(FaseEnRiesgo f) {
        return new FaseEnRiesgoDTO(f.getFaseId(), f.getOrdenTrabajoId(), f.getCreado());
    }
}
