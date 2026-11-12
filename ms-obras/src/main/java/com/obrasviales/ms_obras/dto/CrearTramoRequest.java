package com.obrasviales.ms_obras.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CrearTramoRequest(
        @NotBlank(message = "El nombre del tramo es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
        String nombre,

        @NotNull(message = "La superficie es obligatoria")
        @Positive(message = "La superficie debe ser mayor que 0")
        BigDecimal superficieM2,

        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
        Double latitud,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
        Double longitud) {
}