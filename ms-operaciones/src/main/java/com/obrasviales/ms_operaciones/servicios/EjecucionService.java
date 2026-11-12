package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.clientes.RecursosClient;
import com.obrasviales.ms_operaciones.dtos.CierreOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.CierreOrdenResponse;
import com.obrasviales.ms_operaciones.dtos.AvanceOrdenResponse;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ResolucionClimaticaRequest;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.CierreOrden;
import com.obrasviales.ms_operaciones.modelo.ConsumoMaterial;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.CierreOrdenRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Ejecucion de una orden habilitada: inicio, rechazo del capataz y avances
@Service
@RequiredArgsConstructor
public class EjecucionService {

    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final SuspensionService suspensionService;
    private final ObrasClient obrasClient;
    private final RecursosClient recursosClient;
    private final CierreOrdenRepository cierreOrdenRepository;

    // sin dato climatico confiable la orden no se habilita sola queda pendiente
    // capataz puede HABILITAR confirma la reserva que se conservo; SUSPENDER corre la saga como una suspension por clima.
   
    @Transactional
    public OrdenTrabajoDTO resolverClima(Long ordenId, ResolucionClimaticaRequest request) {
        OrdenTrabajo orden = buscar(ordenId);
        exigirEstado(orden, EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA, "resolver el clima");

        if (request.decision() == ResolucionClimaticaRequest.Decision.HABILITAR) {
            if (orden.getReservaId() == null) {
                throw new ConflictoEstadoException("La orden " + orden.getId() + " no tiene una reserva de recursos vigente.");
            }
            recursosClient.confirmar(orden.getReservaId());
            orden.setEstado(EstadoOrden.HABILITADA);
        } else {
            String motivo = (request.motivo() != null && !request.motivo().isBlank())
                    ? request.motivo()
                    : "Resolución manual del capataz ante un dato climático no confiable.";
            suspensionService.suspender(orden, MotivoSuspension.CLIMA, motivo);
        }
        return OrdenTrabajoDTO.desde(ordenTrabajoRepository.save(orden));
    }

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

    // guardando las horas de maquina y el consumo real de materiales.
    // solo se cierra una vez
    @Transactional
    public CierreOrdenResponse cerrar(Long ordenId, CierreOrdenRequest request) {
        OrdenTrabajo orden = buscar(ordenId);
        exigirEstado(orden, EstadoOrden.EN_EJECUCION, "cerrar la orden");

        CierreOrden cierre = new CierreOrden();
        cierre.setOrdenTrabajoId(orden.getId());
        cierre.setHorasMaquina(request.horasMaquina());
        request.materiales().forEach(m ->
                cierre.getConsumos().add(new ConsumoMaterial(m.material(), m.cantidad(), m.unidad())));
        cierreOrdenRepository.save(cierre);

        orden.setEstado(EstadoOrden.COMPLETADA);
        ordenTrabajoRepository.save(orden);

        List<CierreOrdenRequest.MaterialConsumido> materiales = cierre.getConsumos().stream()
                .map(m -> new CierreOrdenRequest.MaterialConsumido(m.getMaterial(), m.getCantidad(), m.getUnidad()))
                .toList();
        return new CierreOrdenResponse(orden.getId(), orden.getEstado(), cierre.getHorasMaquina(), materiales);
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
