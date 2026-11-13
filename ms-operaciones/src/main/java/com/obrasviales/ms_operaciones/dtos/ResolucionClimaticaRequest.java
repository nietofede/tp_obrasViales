package com.obrasviales.ms_operaciones.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// capataz decide una orden que quedo sin dato climatico confiable,  motivo es opcional y solo se usa al suspender.
public record ResolucionClimaticaRequest(
        @NotNull(message = "La decisión es obligatoria (HABILITAR o SUSPENDER)")
        Decision decision,
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {

    public enum Decision {
        HABILITAR,
        SUSPENDER
    }
}
