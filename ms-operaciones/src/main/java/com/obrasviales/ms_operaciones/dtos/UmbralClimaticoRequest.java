package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.NotNull;

public record UmbralClimaticoRequest(
        @NotNull Double humedadSueloMaxima,
        @NotNull Double probPrecipitacionMaxima,
        @NotNull Double temperaturaMinima
) {}