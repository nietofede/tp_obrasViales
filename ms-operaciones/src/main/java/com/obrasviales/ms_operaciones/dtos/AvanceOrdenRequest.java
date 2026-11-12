package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// orcentaje de avance acumulado de la fase,no puede ser menor al ya registrado.
public record AvanceOrdenRequest(
        @NotNull(message = "El porcentaje de avance es obligatorio")
        @Min(value = 1, message = "El avance debe ser de al menos 1 %")
        @Max(value = 100, message = "El avance no puede superar el 100 %")
        Integer porcentaje) {
}
