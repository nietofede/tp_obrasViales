package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

// fase como la devuelve ms-obras

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContextoFaseDTO(
        Long faseId,
        String tipoTrabajo,
        String estadoFase,
        boolean faseAnteriorCompletada,
        Long tramoId,
        String tramoNombre,
        BigDecimal superficieM2,
        Double latitud,
        Double longitud,
        List<MaterialDTO> materiales) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MaterialDTO(String material, BigDecimal cantidad, String unidad) {
    }
}
