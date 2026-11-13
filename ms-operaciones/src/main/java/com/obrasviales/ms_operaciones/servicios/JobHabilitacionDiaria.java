package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.ResultadoHabilitacionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Dispara todos los dias el mismo proceso que POST /api/operaciones/habilitaciones-diarias.
// No reimplementa la habilitacion: la logica (Obras, Recursos, clima y saga de suspension) vive en
// HabilitacionDiariaService / OrdenTrabajoServiceImpl.habilitarOrden. Es reejecutable: solo toma
// ordenes PROGRAMADA o RECURSOS_ASIGNADOS de la fecha.
@Slf4j
@Component
@RequiredArgsConstructor
public class JobHabilitacionDiaria {

    private final HabilitacionDiariaService habilitacionDiariaService;

    @Scheduled(cron = "${app.jobs.habilitacion.cron:0 0 6 * * ?}")
    public void ejecutarEvaluacionDiaria() {
        ejecutar(LocalDate.now());
    }

    List<ResultadoHabilitacionDTO> ejecutar(LocalDate fecha) {
        log.info("Job diario de habilitación: inicio para {}.", fecha);
        try {
            List<ResultadoHabilitacionDTO> resultados = habilitacionDiariaService.habilitarDelDia(fecha);
            registrarResumen(fecha, resultados);
            return resultados;
        } catch (RuntimeException e) {
            // un fallo general (por ejemplo, la base caida) no debe tirar el scheduler; se reintenta al otro dia o a mano
            log.error("Job diario de habilitación: falló para {}: {}", fecha, e.getMessage(), e);
            return List.of();
        }
    }

    private void registrarResumen(LocalDate fecha, List<ResultadoHabilitacionDTO> resultados) {
        if (resultados.isEmpty()) {
            log.info("Job diario de habilitación: no hay órdenes para habilitar el {}.", fecha);
            return;
        }
        resultados.stream()
                .filter(r -> r.motivo() != null)
                .forEach(r -> log.warn("Orden {} no se habilitó (queda {}): {}", r.ordenId(), r.estado(), r.motivo()));

        Map<String, Long> porEstado = resultados.stream()
                .collect(Collectors.groupingBy(r -> String.valueOf(r.estado()), Collectors.counting()));
        log.info("Job diario de habilitación: {} órdenes procesadas el {} -> {}.", resultados.size(), fecha, porEstado);
    }
}
