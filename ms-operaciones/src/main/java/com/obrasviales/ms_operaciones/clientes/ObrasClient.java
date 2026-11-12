package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;

public interface ObrasClient {

    // pide el contexto de una fase: tipo de trabajo, tramo, coordenadas, materiales y si la fase previa esta completa
    ContextoFaseDTO obtenerContextoFase(Long faseId);
}
