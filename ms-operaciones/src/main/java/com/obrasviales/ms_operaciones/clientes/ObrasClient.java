package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;

import java.time.LocalDate;

public interface ObrasClient {

    // pide el contexto de una fase: tipo de trabajo, tramo, coordenadas, materiales y si la fase previa esta completa
    ContextoFaseDTO obtenerContextoFase(Long faseId);

    // Reprograma la fase a otra fecha de inicio
    FaseDTO reprogramarFase(Long faseId, LocalDate nuevaFechaInicio);

    FaseDTO marcarFaseEnRiesgo(Long faseId);
}
