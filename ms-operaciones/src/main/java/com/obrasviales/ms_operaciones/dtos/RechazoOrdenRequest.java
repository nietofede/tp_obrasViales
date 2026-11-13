package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazoOrdenRequest(
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
