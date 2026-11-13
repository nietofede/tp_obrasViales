package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

// Fase como la devuelve ms-obras al reprogramarla o marcarla en riesgo.
@JsonIgnoreProperties(ignoreUnknown = true)
public record FaseDTO(
        Long id,
        Long tramoId,
        String tipo,
        String estado,
        LocalDate fechaInicioPlanificada,
        LocalDate fechaFinPlanificada,
        Long faseAnteriorId,
        BigDecimal avance) {
}
