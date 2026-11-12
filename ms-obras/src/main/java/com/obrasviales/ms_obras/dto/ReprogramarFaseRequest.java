package com.obrasviales.ms_obras.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReprogramarFaseRequest(
        @NotNull(message = "La nueva fecha de inicio es obligatoria")
        LocalDate nuevaFechaInicio) {
}