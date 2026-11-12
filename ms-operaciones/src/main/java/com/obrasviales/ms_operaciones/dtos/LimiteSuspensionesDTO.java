package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LimiteSuspensionesDTO(
        @NotNull(message = "El límite es obligatorio")
        @Min(value = 1, message = "El límite debe ser al menos 1")
        Integer limite) {
}
