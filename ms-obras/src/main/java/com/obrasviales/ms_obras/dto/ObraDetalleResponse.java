package com.obrasviales.ms_obras.dto;

import java.util.List;

public record ObraDetalleResponse(
        Long id,
        String nombre,
        String descripcion,
        String estado,
        int avance,
        List<TramoConFases> tramos) {

    public record TramoConFases(TramoResponse tramo, List<FaseResponse> fases) {
    }
}