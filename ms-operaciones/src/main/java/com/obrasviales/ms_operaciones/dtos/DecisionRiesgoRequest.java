package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// decision reprogramar solamente exige nuevaFechaInicio, cancelar no
public record DecisionRiesgoRequest(
        @NotNull(message = "La decisión es obligatoria (REPROGRAMAR o CANCELAR)")
        DecisionRiesgo decision,
        LocalDate nuevaFechaInicio) {

    public enum DecisionRiesgo {
        REPROGRAMAR,
        CANCELAR
    }
}
