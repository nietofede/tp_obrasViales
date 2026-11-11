package com.obrasviales.ms_obras.dto;

import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;

import java.time.LocalDate;

public record FaseResponse(
        Long id,
        Long tramoId,
        TipoFase tipo,
        EstadoFase estado,
        LocalDate fechaInicioPlanificada,
        LocalDate fechaFinPlanificada,
        Long faseAnteriorId) {

    public static FaseResponse desde(Fase fase) {
        return new FaseResponse(
                fase.getId(),
                fase.getTramo() != null ? fase.getTramo().getId() : null,
                fase.getTipo(),
                fase.getEstado(),
                fase.getFechaInicioPlanificada(),
                fase.getFechaFinPlanificada(),
                fase.getFaseAnterior() != null ? fase.getFaseAnterior().getId() : null);
    }
}