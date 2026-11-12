package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReprogramacionOrdenRequest(
        @NotNull(message = "La nueva fecha es obligatoria")
        @FutureOrPresent(message = "La nueva fecha no puede ser anterior a hoy")
        LocalDate fecha) {
}
