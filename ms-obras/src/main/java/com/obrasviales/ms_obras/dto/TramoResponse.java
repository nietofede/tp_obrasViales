package com.obrasviales.ms_obras.dto;

import com.obrasviales.ms_obras.domain.Tramo;

import java.math.BigDecimal;

public record TramoResponse(
        Long id,
        Long obraId,
        String nombre,
        BigDecimal superficieM2,
        Double latitud,
        Double longitud) {

    public static TramoResponse desde(Tramo tramo) {
        return new TramoResponse(
                tramo.getId(),
                tramo.getObra() != null ? tramo.getObra().getId() : null,
                tramo.getNombre(),
                tramo.getSuperficieM2(),
                tramo.getLatitud(),
                tramo.getLongitud());
    }
}