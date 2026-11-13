package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoRequest;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoRequest.DecisionRiesgo;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoResponse;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.dtos.FaseEnRiesgoDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.FaseEnRiesgoRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.SuspensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// aca es cuando una fase ya llegó al limite  
@Service
@RequiredArgsConstructor
public class FaseEnRiesgoService {

    private final FaseEnRiesgoRepository faseEnRiesgoRepository;
    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final SuspensionRepository suspensionRepository;
    private final ObrasClient obrasClient;

    @Transactional(readOnly = true)
    public List<FaseEnRiesgoDTO> listarPendientes() {
        return faseEnRiesgoRepository.findByResueltaFalseOrderByCreadoAsc().stream()
                .map(FaseEnRiesgoDTO::desde)
                .toList();
    }

    // obras reprograma la fase (sale de EN_RIESGO y corre las dependientes), se crea una orden nueva y la
    // suspendida queda REPROGRAMADA. 
    // CANCELAR: la orden suspendida queda CANCELADA. En los dos casos el conteo de suspensiones de la fase vuelve a 0.
    @Transactional
    public DecisionRiesgoResponse decidir(Long faseId, DecisionRiesgoRequest request) {
        FaseEnRiesgo enRiesgo = faseEnRiesgoRepository.findByFaseIdAndResueltaFalse(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay una decisión pendiente para la fase " + faseId + "."));
        OrdenTrabajo orden = ordenTrabajoRepository.findById(enRiesgo.getOrdenTrabajoId())
                .orElseThrow(() -> new OrdenNoEncontrada(enRiesgo.getOrdenTrabajoId()));
        if (orden.getEstado() != EstadoOrden.SUSPENDIDA) {
            throw new ConflictoEstadoException("La orden " + orden.getId() + " de la fase no está SUSPENDIDA (estado "
                    + orden.getEstado() + ").");
        }

        Long nuevaOrdenId = null;
        if (request.decision() == DecisionRiesgo.REPROGRAMAR) {
            LocalDate nuevaFecha = request.nuevaFechaInicio();
            if (nuevaFecha == null) {
                throw new ReglaNegocioException("Para reprogramar hay que indicar la nueva fecha de inicio.", "DECISION-FECHA");
            }
            FaseDTO fase = obrasClient.reprogramarFase(faseId, nuevaFecha);

            OrdenTrabajo nueva = new OrdenTrabajo();
            nueva.setFaseId(faseId);
            nueva.setTramoId(orden.getTramoId());
            nueva.setFechaProgramada(fase.fechaInicioPlanificada() != null ? fase.fechaInicioPlanificada() : nuevaFecha);
            nueva.setEstado(EstadoOrden.PROGRAMADA);
            nueva.setContadorSuspensiones(0);
            nuevaOrdenId = ordenTrabajoRepository.save(nueva).getId();

            orden.setEstado(EstadoOrden.REPROGRAMADA);
        } else {
            orden.setEstado(EstadoOrden.CANCELADA);
        }
        ordenTrabajoRepository.save(orden);

        suspensionRepository.marcarNoVigentes(faseId);
        enRiesgo.setResuelta(true);
        enRiesgo.setDecision(request.decision().name());
        enRiesgo.setResueltaEn(LocalDateTime.now());
        faseEnRiesgoRepository.save(enRiesgo);

        return new DecisionRiesgoResponse(faseId, request.decision(), orden.getId(), orden.getEstado(), nuevaOrdenId);
    }
}
