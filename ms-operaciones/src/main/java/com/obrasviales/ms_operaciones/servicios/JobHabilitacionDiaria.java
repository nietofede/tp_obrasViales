package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobHabilitacionDiaria {

    private final OTServiceInterfaz otService;
    private final AuthSistemaService authSistemaService;

    // Ejecuta a las 06:00 AM o según app.jobs.habilitacion.cron
    @Scheduled(cron = "${app.jobs.habilitacion.cron:0 0 6 * * ?}")
    public void ejecutarEvaluacionDiaria() {
        log.info("Iniciando Job diario de habilitación de órdenes de trabajo...");
        try {
            // 1. Validación de autenticación con cuenta de servicio SISTEMA (Client Credentials)
            String token = authSistemaService.obtenerTokenSistema();
            log.info("Token de servicio SISTEMA obtenido correctamente (longitud: {}).", token.length());

            LocalDate hoy = LocalDate.now();

            // 2. Consulta idempotente: solo órdenes pendientes de evaluar en el día
            List<OrdenTrabajoDTO> pendientes = otService.listarPorFechaYEstado(hoy, EstadoOrden.EVALUACION_CLIMATICA);

            if (pendientes.isEmpty()) {
                log.info("Job Idempotente: No hay órdenes pendientes de evaluación para la fecha {}.", hoy);
                return;
            }

            log.info("Procesando {} órdenes de trabajo programadas...", pendientes.size());

            for (OrdenTrabajoDTO orden : pendientes) {
                procesarOrden(orden);
            }

            log.info("Job diario de habilitación finalizado con éxito.");
        } catch (Exception e) {
            log.error("Error durante la ejecución del Job diario de habilitación: {}", e.getMessage(), e);
        }
    }

    private void procesarOrden(OrdenTrabajoDTO orden) {
        if (orden.getTramoId().equals(1L)) {
            otService.cambiarEstado(orden.getId(), EstadoOrden.HABILITADA);
            log.info("Orden ID {}: HABILITADA (condiciones óptimas)", orden.getId());
        } else if (orden.getTramoId().equals(2L)) {
            otService.cambiarEstado(orden.getId(), EstadoOrden.SUSPENDIDA);
            log.info("Orden ID {}: SUSPENDIDA (clima adverso)", orden.getId());
        } else {
            otService.cambiarEstado(orden.getId(), EstadoOrden.CANCELADA);
            log.info("Orden ID {}: CANCELADA (exceso de distancia)", orden.getId());
        }
    }
}