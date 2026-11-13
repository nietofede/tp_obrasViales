package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HabilitacionDiariaRequest(@NotNull(message = "La fecha es obligatoria") LocalDate fecha) {
}
