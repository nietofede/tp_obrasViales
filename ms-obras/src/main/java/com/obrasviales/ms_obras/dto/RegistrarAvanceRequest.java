package com.obrasviales.ms_obras.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RegistrarAvanceRequest(
        @NotNull(message = "El porcentaje de avance es obligatorio")
        @Min(value = 1, message = "El avance debe ser de al menos 1 %")
        @Max(value = 100, message = "El avance no puede superar el 100 %")
        Integer porcentaje) {
}