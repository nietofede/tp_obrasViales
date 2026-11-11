package com.obrasviales.ms_obras.dto;

import com.obrasviales.ms_obras.domain.TipoFase;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CrearFaseRequest(
        @NotNull(message = "El tipo de fase es obligatorio")
        TipoFase tipo,

        @NotNull(message = "La fecha de inicio planificada es obligatoria")
        LocalDate fechaInicioPlanificada,

        @NotNull(message = "La fecha de fin planificada es obligatoria")
        LocalDate fechaFinPlanificada,

        Long faseAnteriorId) {
}