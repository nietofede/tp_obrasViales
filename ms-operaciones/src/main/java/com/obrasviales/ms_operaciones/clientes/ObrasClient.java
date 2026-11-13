package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;

import java.time.LocalDate;
import java.util.Set;

public interface ObrasClient {

    // pide el contexto de una fase: tipo de trabajo, tramo, coordenadas, materiales y si la fase previa esta completa
    ContextoFaseDTO obtenerContextoFase(Long faseId);

    // Reprograma la fase a otra fecha de inicio
    FaseDTO reprogramarFase(Long faseId, LocalDate nuevaFechaInicio);

    FaseDTO marcarFaseEnRiesgo(Long faseId);

    // Registra el avance real de la fase como porcentaje, al llegar a 100 la completa.
    FaseDTO registrarAvance(Long faseId, int porcentaje);

    // Ids de los tramos de una obra. Se consulta con el token del usuario que hace el pedido
    // (PLANIFICADOR, CAPATAZ o AUDITOR, los mismos roles que Obras admite para listar tramos).
    Set<Long> tramosDeObra(Long obraId);
}
