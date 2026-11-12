package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ResultadoHabilitacionDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class HabilitacionDiariaService {

    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final OTServiceInterfaz ordenService;

    public List<ResultadoHabilitacionDTO> habilitarDelDia(LocalDate fecha) {
        List<OrdenTrabajo> ordenes = new ArrayList<>(
                ordenTrabajoRepository.findByFechaProgramadaAndEstado(fecha, EstadoOrden.PROGRAMADA));
        ordenes.addAll(ordenTrabajoRepository.findByFechaProgramadaAndEstado(fecha, EstadoOrden.RECURSOS_ASIGNADOS));

        List<ResultadoHabilitacionDTO> resultados = new ArrayList<>();
        for (OrdenTrabajo orden : ordenes) {
            try {
                OrdenTrabajoDTO resultado = ordenService.habilitarOrden(orden.getId());
                resultados.add(new ResultadoHabilitacionDTO(orden.getId(), resultado.getEstado(), null));
            } catch (RuntimeException e) {
                // la orden queda como estaba 
                log.warn("No se pudo habilitar la orden {}: {}", orden.getId(), e.getMessage());
                resultados.add(new ResultadoHabilitacionDTO(orden.getId(), orden.getEstado(), motivoDe(e)));
            }
        }
        return resultados;
    }

    // Solo se informa el mensaje de los errores de negocio o de servicios externos; el resto es un error inesperado.
    private String motivoDe(RuntimeException e) {
        if (e instanceof ReglaNegocioException || e instanceof ConflictoEstadoException
                || e instanceof RecursoNoEncontradoException || e instanceof ServicioNoDisponibleException) {
            return e.getMessage();
        }
        return "Error inesperado al habilitar la orden.";
    }
}
