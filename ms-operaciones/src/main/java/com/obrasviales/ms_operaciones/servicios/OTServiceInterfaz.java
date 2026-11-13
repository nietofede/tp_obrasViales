package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;

import java.time.LocalDate;
import java.util.List;

// definimos una interfaz que despues vamos a usar en el otro archivo 
public interface OTServiceInterfaz {
    OrdenTrabajoDTO crearOrden(CrearOrdenRequest request);
    OrdenTrabajoDTO obtenerPorId(Long id);
    List<OrdenTrabajoDTO> listar(LocalDate fecha, EstadoOrden estado, Long obraId);
    OrdenTrabajoDTO habilitarOrden(Long ordenId);
    List<EvaluacionClimatica> obtenerHistorialClimatico(Long ordenId);

    OrdenTrabajoDTO cancelarOrden(Long id);
    OrdenTrabajoDTO reprogramarOrden(Long id, LocalDate nuevaFecha);
}
