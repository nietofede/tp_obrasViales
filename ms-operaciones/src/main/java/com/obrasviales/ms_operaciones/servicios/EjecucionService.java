package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.AvanceOrdenResponse;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ejecucion de una orden habilitada: inicio, rechazo del capataz y avances
@Service
@RequiredArgsConstructor
public class EjecucionService {

    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final SuspensionService suspensionService;
    private final ObrasClient obrasClient;

    // de habilitada a ejecucion, el primer avance es el que inicia la fase alla.
    @Transactional
    public OrdenTrabajoDTO iniciar(Long ordenId) {
        OrdenTrabajo orden = buscar(ordenId);
        exigirEstado(orden, EstadoOrden.HABILITADA, "iniciar la ejecución");
        orden.setEstado(EstadoOrden.EN_EJECUCION);
        return OrdenTrabajoDTO.desde(ordenTrabajoRepository.save(orden));
    }

    // si capataz rechaza una orden habilitada: se suspende con su motivo y corre la saga
    @Transactional
    public OrdenTrabajoDTO rechazar(Long ordenId, String motivo) {
        OrdenTrabajo orden = buscar(ordenId);
        exigirEstado(orden, EstadoOrden.HABILITADA, "rechazar la orden");
        suspensionService.suspender(orden, MotivoSuspension.RECHAZO, motivo);
        return OrdenTrabajoDTO.desde(ordenTrabajoRepository.save(orden));
    }

    // reenvia el avance a Obras
    public AvanceOrdenResponse registrarAvance(Long ordenId, int porcentaje) {
        OrdenTrabajo orden = buscar(ordenId);
        exigirEstado(orden, EstadoOrden.EN_EJECUCION, "registrar avances");
        FaseDTO fase = obrasClient.registrarAvance(orden.getFaseId(), porcentaje);
        return new AvanceOrdenResponse(orden.getId(), orden.getFaseId(), fase.avance(), fase.estado());
    }

    private OrdenTrabajo buscar(Long ordenId) {
        return ordenTrabajoRepository.findById(ordenId).orElseThrow(() -> new OrdenNoEncontrada(ordenId));
    }

    // solo transiciones validas
    private void exigirEstado(OrdenTrabajo orden, EstadoOrden requerido, String accion) {
        if (orden.getEstado() != requerido) {
            throw new TransicionInvalida("Para " + accion + " la orden debe estar " + requerido
                    + " (estado actual: " + orden.getEstado() + ").");
        }
    }
}
