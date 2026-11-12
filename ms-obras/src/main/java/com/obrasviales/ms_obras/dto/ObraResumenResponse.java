package com.obrasviales.ms_obras.dto;

public record ObraResumenResponse(
        Long id,
        String nombre,
        String descripcion,
        String estado,
        int avance) {
}