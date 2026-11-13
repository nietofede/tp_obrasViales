package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.clientes.RecursosClient;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.dtos.SuspensionDTO;
import com.obrasviales.ms_operaciones.modelo.ConfiguracionSuspensiones;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.Suspension;
import com.obrasviales.ms_operaciones.repositorios.ConfiguracionSuspensionesRepository;
import com.obrasviales.ms_operaciones.repositorios.FaseEnRiesgoRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.SuspensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// Saga de suspension, orquestada desde Operaciones: liberar la reserva en Recursos, registrar la suspension y,
// segun cuantas lleve la fase (RN-12), o bien reprogramar la fase en Obras (RN-02) y crear la orden nueva, o bien
// marcar la fase en riesgo y esperar al PLANIFICADOR. No tiene @Transactional propio: corre dentro de la transaccion
// de quien la llama, asi si falla un paso de Obras se deshace lo local (lo ya liberado en Recursos se repite sin dano).
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspensionService {

    private final SuspensionRepository suspensionRepository;
    private final ConfiguracionSuspensionesRepository configuracionRepository;
    private final FaseEnRiesgoRepository faseEnRiesgoRepository;
    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final RecursosClient recursosClient;
    private final ObrasClient obrasClient;

    // Suspende la orden. Al terminar, la orden queda REPROGRAMADA (hay una orden nueva PROGRAMADA) o SUSPENDIDA
    // (fase en riesgo, decide el PLANIFICADOR).
    public void suspender(OrdenTrabajo orden, MotivoSuspension motivo, String detalle) {
        liberarReserva(orden);
        orden.setEstado(EstadoOrden.SUSPENDIDA);

        Suspension suspension = new Suspension();
        suspension.setOrdenTrabajoId(orden.getId());
        suspension.setFaseId(orden.getFaseId());
        suspension.setMotivo(motivo);
        suspension.setDetalle(detalle);
        suspensionRepository.save(suspension);

        long vigentes = suspensionRepository.countByFaseIdAndVigenteTrue(orden.getFaseId());
        orden.setContadorSuspensiones((int) vigentes);

        if (vigentes >= limiteSuspensiones()) {
            marcarFaseEnRiesgo(orden);
        } else {
            reprogramar(orden);
        }
    }

    // Historial: por orden, por fase, o todo si no se filtra (el filtro por orden tiene prioridad).
    @Transactional(readOnly = true)
    public List<SuspensionDTO> historial(Long faseId, Long ordenId) {
        List<Suspension> suspensiones;
        if (ordenId != null) {
            suspensiones = suspensionRepository.findByOrdenTrabajoIdOrderByFechaDesc(ordenId);
        } else if (faseId != null) {
            suspensiones = suspensionRepository.findByFaseIdOrderByFechaDesc(faseId);
        } else {
            suspensiones = suspensionRepository.findAllByOrderByFechaDesc();
        }
        return suspensiones.stream().map(SuspensionDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public int limiteSuspensiones() {
        return configuracionRepository.findById(ConfiguracionSuspensiones.ID_UNICO)
                .map(ConfiguracionSuspensiones::getLimiteSuspensiones)
                .orElseThrow(() -> new IllegalStateException("Falta la configuración del límite de suspensiones."));
    }

    @Transactional
    public int actualizarLimite(int nuevoLimite) {
        ConfiguracionSuspensiones configuracion = configuracionRepository.findById(ConfiguracionSuspensiones.ID_UNICO)
                .orElseGet(ConfiguracionSuspensiones::new);
        configuracion.setLimiteSuspensiones(nuevoLimite);
        return configuracionRepository.save(configuracion).getLimiteSuspensiones();
    }

    // Si Recursos no responde no se frena la suspension: la reserva provisoria vence sola por TTL (30 min).
    public void liberarReserva(OrdenTrabajo orden) {
        if (orden.getReservaId() != null) {
            try {
                recursosClient.liberar(orden.getReservaId());
            } catch (RuntimeException e) {
                log.warn("No se pudo liberar la reserva {} de la orden {}; vence por TTL: {}",
                        orden.getReservaId(), orden.getId(), e.getMessage());
            }
        }
        orden.setReservaId(null);
        orden.setHoraSalidaCamion(null);
    }

    // Debajo del limite: se corre la fase (Obras la corre en cascada con sus dependientes) y se crea la orden nueva.
    // La fecha nueva es la mayor entre el dia siguiente a la orden y el fin de la fase anterior: antes de eso
    // Obras la rechaza por RN-02 y la saga fallaria.
    private void reprogramar(OrdenTrabajo orden) {
        LocalDate nuevaFecha = orden.getFechaProgramada().plusDays(1);
        LocalDate finAnterior = obrasClient.obtenerContextoFase(orden.getFaseId()).fechaFinFaseAnterior();
        if (finAnterior != null && finAnterior.isAfter(nuevaFecha)) {
            nuevaFecha = finAnterior;
        }
        FaseDTO fase = obrasClient.reprogramarFase(orden.getFaseId(), nuevaFecha);

        OrdenTrabajo nueva = new OrdenTrabajo();
        nueva.setFaseId(orden.getFaseId());
        nueva.setTramoId(orden.getTramoId());
        nueva.setFechaProgramada(fase.fechaInicioPlanificada() != null ? fase.fechaInicioPlanificada() : nuevaFecha);
        nueva.setEstado(EstadoOrden.PROGRAMADA);
        nueva.setContadorSuspensiones(0);
        ordenTrabajoRepository.save(nueva);

        orden.setEstado(EstadoOrden.REPROGRAMADA);
    }

    // En el limite: la fase queda en riesgo en Obras y no se crea orden nueva hasta que decida el PLANIFICADOR.
    private void marcarFaseEnRiesgo(OrdenTrabajo orden) {
        obrasClient.marcarFaseEnRiesgo(orden.getFaseId());
        if (!faseEnRiesgoRepository.existsByFaseIdAndResueltaFalse(orden.getFaseId())) {
            FaseEnRiesgo enRiesgo = new FaseEnRiesgo();
            enRiesgo.setFaseId(orden.getFaseId());
            enRiesgo.setOrdenTrabajoId(orden.getId());
            faseEnRiesgoRepository.save(enRiesgo);
        }
    }
}
