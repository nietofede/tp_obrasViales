package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobHabilitacionDiaria {

    private final OTServiceInterfaz otService;
    private final RestClient sistemaRestClient; // Bean de obras-commons con interceptor de token SISTEMA
    private final OrdenTrabajoRepository ordenTrabajoRepository;

    @Value("${app.servicios.obras-url:http://localhost:8082}")
    private String obrasUrl;

    @Value("${app.servicios.recursos-url:http://localhost:8083}")
    private String recursosUrl;

    @Scheduled(cron = "${app.jobs.habilitacion.cron:0 0 6 * * ?}")
    public void ejecutarEvaluacionDiaria() {
        log.info("Iniciando Job diario de habilitación de órdenes de trabajo...");
        LocalDate hoy = LocalDate.now();

        // Consulta idempotente: solo toma órdenes en estado PROGRAMADA para hoy
        List<OrdenTrabajoDTO> programadas = otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA);

        if (programadas.isEmpty()) {
            log.info("Job Idempotente: No hay órdenes en estado PROGRAMADA para la fecha {}.", hoy);
            return;
        }

        log.info("Procesando {} órdenes programadas para {}...", programadas.size(), hoy);
        for (OrdenTrabajoDTO orden : programadas) {
            try {
                procesarOrden(orden);
            } catch (Exception e) {
                log.error("Error procesando orden ID {}: {}", orden.getId(), e.getMessage(), e);
            }
        }
        log.info("Job diario de habilitación finalizado.");
    }

    @Transactional
    protected void procesarOrden(OrdenTrabajoDTO ordenDto) {
        Long ordenId = ordenDto.getId();
        Long faseId = ordenDto.getFaseId();

        OrdenTrabajo ordenEntidad = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada: " + ordenId));

        // 1. Consultar datos del tramo/fase en ms-obras usando sistemaRestClient
        DatosTramoFase datosObra = obtenerDatosObra(faseId);
        if (!datosObra.faseAnteriorCompletada()) {
            log.warn("Orden ID {}: la fase anterior no está completa (RN-01). Queda PROGRAMADA.", ordenId);
            return;
        }

        // 2. Reservar recursos en ms-recursos
        Long reservaId = reservarRecursos(ordenDto, datosObra);
        if (reservaId == null) {
            otService.cambiarEstado(ordenId, EstadoOrden.CANCELADA);
            log.warn("Orden ID {}: CANCELADA por falta de disponibilidad o exceso de distancia en recursos.", ordenId);
            return;
        }

        ordenEntidad.setReservaId(reservaId);
        ordenTrabajoRepository.save(ordenEntidad);
        otService.cambiarEstado(ordenId, EstadoOrden.RECURSOS_ASIGNADOS);
        log.info("Orden ID {}: Recursos asignados en estado PRE_RESERVADA (Reserva ID {}).", ordenId, reservaId);

        // 3. Evaluar y actualizar estado climático usando el servicio de Fede
        OrdenTrabajoDTO evaluada = otService.evaluarYActualizarEstadoClimatico(
                ordenId,
                datosObra.latitud(),
                datosObra.longitud(),
                datosObra.tipoTrabajo()
        );

        // 4. Acciones posteriores según el resultado de la evaluación climática
        if (evaluada.getEstado() == EstadoOrden.HABILITADA) {
            confirmarReservaRecursos(reservaId);
            log.info("Orden ID {}: HABILITADA y reserva {} CONFIRMADA.", ordenId, reservaId);

        } else if (evaluada.getEstado() == EstadoOrden.SUSPENDIDA) {
            // 4.a Liberar la reserva de recursos
            liberarReservaRecursos(reservaId);

            // 4.b Reprogramar para el día siguiente en ms-obras y crear nueva orden
            LocalDate nuevaFecha = ordenDto.getFechaProgramada().plusDays(1);
            reprogramarFaseEnObras(faseId, nuevaFecha);

            CrearOrdenRequest nuevaOrdenReq = new CrearOrdenRequest();
            nuevaOrdenReq.setFaseId(faseId);
            nuevaOrdenReq.setTramoId(ordenDto.getTramoId());
            nuevaOrdenReq.setFechaProgramada(nuevaFecha);

            OrdenTrabajoDTO nuevaOrden = otService.crearOrden(nuevaOrdenReq);
            otService.cambiarEstado(ordenId, EstadoOrden.REPROGRAMADA);

            // 4.c Contar suspensiones acumuladas por FASE (no solo por orden individual)
            int suspensionesFase = contarSuspensionesPorFase(faseId);

            OrdenTrabajo nuevaEntidad = ordenTrabajoRepository.findById(nuevaOrden.getId())
                    .orElseThrow(() -> new IllegalStateException("Nueva orden no encontrada: " + nuevaOrden.getId()));
            nuevaEntidad.setContadorSuspensiones(suspensionesFase);
            ordenTrabajoRepository.save(nuevaEntidad);

            log.info("Orden ID {}: SUSPENDIDA y pasada a REPROGRAMADA. Creada nueva orden ID {} para {}. Suspensiones acumuladas de la fase {}: {}.",
                    ordenId, nuevaOrden.getId(), nuevaFecha, faseId, suspensionesFase);

            // RN-06: Si la fase acumula 3 o más suspensiones climáticas, marcar fase en riesgo en ms-obras
            if (suspensionesFase >= 3) {
                marcarFaseEnRiesgo(faseId);
                log.warn("Fase ID {} alcanzó {} suspensiones climáticas. Marcada EN_RIESGO en ms-obras.", faseId, suspensionesFase);
            }
        } else {
            log.warn("Orden ID {}: Quedó en estado {} tras la evaluación climática.", ordenId, evaluada.getEstado());
        }
    }

    private int contarSuspensionesPorFase(Long faseId) {
        List<OrdenTrabajo> ordenesDeLaFase = ordenTrabajoRepository.findByFaseId(faseId);
        // Contamos cuántas órdenes de esta misma fase fueron suspendidas/reprogramadas por clima
        return (int) ordenesDeLaFase.stream()
                .filter(o -> o.getEstado() == EstadoOrden.SUSPENDIDA || o.getEstado() == EstadoOrden.REPROGRAMADA)
                .count();
    }

    @SuppressWarnings("unchecked")
    private DatosTramoFase obtenerDatosObra(Long faseId) {
        Map<String, Object> ctx = sistemaRestClient.get()
                .uri(obrasUrl + "/api/obras/internos/fases/" + faseId + "/contexto-ejecucion")
                .retrieve()
                .body(Map.class);

        if (ctx == null) {
            throw new IllegalStateException("ms-obras devolvió una respuesta vacía para la fase " + faseId);
        }

        return new DatosTramoFase(
                ((Number) ctx.get("latitud")).doubleValue(),
                ((Number) ctx.get("longitud")).doubleValue(),
                TipoTrabajo.valueOf(ctx.get("tipoTrabajo").toString()),
                Boolean.TRUE.equals(ctx.get("faseAnteriorCompletada"))
        );
    }

    @SuppressWarnings("unchecked")
    private Long reservarRecursos(OrdenTrabajoDTO orden, DatosTramoFase datosObra) {
        Map<String, Object> body = Map.of(
                "ordenTrabajoId", orden.getId(),
                "tipoTrabajo", datosObra.tipoTrabajo().name(),
                "fecha", orden.getFechaProgramada().toString(),
                "tramoLat", datosObra.latitud(),
                "tramoLon", datosObra.longitud()
        );

        try {
            Map<String, Object> resp = sistemaRestClient.post()
                    .uri(recursosUrl + "/api/recursos/internos/reservas")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null || resp.get("id") == null) {
                throw new IllegalStateException("ms-recursos respondió sin id de reserva para la orden " + orden.getId());
            }
            return ((Number) resp.get("id")).longValue();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                log.warn("Orden ID {}: Recursos rechazó la reserva: {}", orden.getId(), e.getResponseBodyAsString());
                return null;
            }
            throw e;
        }
    }

    private void confirmarReservaRecursos(Long reservaId) {
        sistemaRestClient.post()
                .uri(recursosUrl + "/api/recursos/internos/reservas/" + reservaId + "/confirmar")
                .retrieve()
                .toBodilessEntity();
    }

    private void liberarReservaRecursos(Long reservaId) {
        sistemaRestClient.post()
                .uri(recursosUrl + "/api/recursos/internos/reservas/" + reservaId + "/liberar")
                .retrieve()
                .toBodilessEntity();
    }

    private void reprogramarFaseEnObras(Long faseId, LocalDate nuevaFechaInicio) {
        Map<String, Object> body = Map.of("nuevaFechaInicio", nuevaFechaInicio.toString());
        sistemaRestClient.patch()
                .uri(obrasUrl + "/api/obras/fases/" + faseId + "/programacion")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void marcarFaseEnRiesgo(Long faseId) {
        sistemaRestClient.patch()
                .uri(obrasUrl + "/api/obras/internos/fases/" + faseId + "/riesgo")
                .retrieve()
                .toBodilessEntity();
    }

    private record DatosTramoFase(Double latitud, Double longitud, TipoTrabajo tipoTrabajo, boolean faseAnteriorCompletada) {}
}