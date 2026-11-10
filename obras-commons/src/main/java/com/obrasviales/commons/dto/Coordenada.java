package com.obrasviales.commons.dto;

import java.io.Serializable;

public record Coordenada(Double latitud, Double longitud) implements Serializable {

    public Coordenada {
        if (latitud == null || latitud < -90.0 || latitud > 90.0) {
            throw new IllegalArgumentException("Latitud inválida. Debe estar entre -90 y 90.");
        }
        if (longitud == null || longitud < -180.0 || longitud > 180.0) {
            throw new IllegalArgumentException("Longitud inválida. Debe estar entre -180 y 180.");
        }
    }

    // crep que asi lo requiere Osrm
    public String toOsrmString() {
        return longitud + "," + latitud;
    }
}