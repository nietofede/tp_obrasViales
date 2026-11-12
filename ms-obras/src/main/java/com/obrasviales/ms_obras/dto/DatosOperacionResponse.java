package com.obrasviales.ms_obras.dto;

import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.TipoFase;

import java.math.BigDecimal;
import java.util.List;

public record DatosOperacionResponse(
        Long faseId,
        TipoFase tipoTrabajo,
        EstadoFase estadoFase,
        boolean faseAnteriorCompletada,
        Long tramoId,
        String tramoNombre,
        BigDecimal superficieM2,
        Double latitud,
        Double longitud,
        List<MaterialCalculado> materiales) {
}