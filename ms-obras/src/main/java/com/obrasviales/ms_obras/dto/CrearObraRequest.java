package com.obrasviales.ms_obras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearObraRequest(
        @NotBlank(message = "El nombre de la obra es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
        String nombre,

        @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
        String descripcion) {
}