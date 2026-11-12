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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobHabilitacionDiaria {

    private final OTServiceInterfaz otService;
    private final RestClient sistemaRestClient;
    private final OrdenTrabajoRepository ordenTrabajoRepository;

    @Value("${app.servicios.obras-url:http://localhost:8082}")
    private String obrasUrl;

    @Value("${app.servicios.recursos-url:http://localhost:8083}")
    private String recursosUrl;

    @Scheduled(cron = "${app.jobs.habilitacion.cron:0 0 6 * * ?}")
    public void ejecutarEvaluacionDiaria() {
        log.info("Iniciando Job diario de habilitación de órdenes de trabajo...");
        LocalDate hoy = LocalDate.now();

        // 1. Procesar órdenes PROGRAMADA para hoy
        List<OrdenTrabajoDTO> programadas = otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA);
        if (programadas.isEmpty()) {
            log.info("Job Idempotente: No hay órdenes en estado PROGRAMADA para la fecha {}.", hoy);
        } else {
            log.info("Procesando {} órdenes PROGRAMADA para {}...", programadas.size(), hoy);
            for (OrdenTrabajoDTO orden : programadas) {
                try {
                    procesarOrden(orden);
                } catch (Exception e) {
                    log.error("Error procesando orden PROGRAMADA ID {}: {}", orden.getId(), e.getMessage(), e);
                }
            }
        }

        // 2. Retomar órdenes SUSPENDIDA de hoy que no tengan orden hija
        List<OrdenTrabajoDTO> suspendidasHoy = otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA);
        for (OrdenTrabajoDTO suspendida : suspendidasHoy) {
            try {
                if (!tieneOrdenHija(suspendida)) {
                    log.info("Retomando compensación de saga para orden SUSPENDIDA ID {} sin orden hija...", suspendida.getId());
                    compensarOrdenSuspendida(suspendida, suspendida.getReservaId());
                }
            } catch (Exception e) {
                log.error("Error retomando orden SUSPENDIDA ID {}: {}", suspendida.getId(), e.getMessage(), e);
            }
        }

        log.info("Job diario de habilitación finalizado.");
    }

    protected void procesarOrden(OrdenTrabajoDTO ordenDto) {
        Long ordenId = ordenDto.getId();
        Long faseId = ordenDto.getFaseId();

        OrdenTrabajo ordenEntidad = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada: " + ordenId));

        // 1. Consultar datos del tramo/fase en ms-obras
        DatosTramoFase datosObra = obtenerDatosObra(faseId);
        if (!datosObra.faseAnteriorCompletada()) {
            log.warn("Orden ID {}: la fase anterior no está completa (RN-01). Queda PROGRAMADA.", ordenId);
            return;
        }

        // 2. Reservar recursos en ms-recursos y guardar horaSalidaCamion
        ResultadoReserva resultadoReserva = reservarRecursos(ordenDto, datosObra);
        if (resultadoReserva == null) {
            otService.cambiarEstado(ordenId, EstadoOrden.CANCELADA);
            log.warn("Orden ID {}: CANCELADA por rechazo de reserva en ms-recursos (422).", ordenId);
            return;
        }

        ordenEntidad.setReservaId(resultadoReserva.reservaId());
        if (resultadoReserva.horaSalidaCamion() != null) {
            ordenEntidad.setHoraSalidaCamion(resultadoReserva.horaSalidaCamion());
        }
        ordenTrabajoRepository.save(ordenEntidad);
        otService.cambiarEstado(ordenId, EstadoOrden.RECURSOS_ASIGNADOS);
        log.info("Orden ID {}: Recursos asignados (Reserva ID {}, Hora salida camión: {}).",
                ordenId, resultadoReserva.reservaId(), resultadoReserva.horaSalidaCamion());

        // 3. Evaluar y actualizar estado climático
        OrdenTrabajoDTO evaluada = otService.evaluarYActualizarEstadoClimatico(
                ordenId,
                datosObra.latitud(),
                datosObra.longitud(),
                datosObra.tipoTrabajo()
        );

        // 4. Acciones posteriores según el resultado de la evaluación climática
        if (evaluada.getEstado() == EstadoOrden.HABILITADA) {
            confirmarReservaRecursos(resultadoReserva.reservaId());
            log.info("Orden ID {}: HABILITADA y reserva {} CONFIRMADA.", ordenId, resultadoReserva.reservaId());

        } else if (evaluada.getEstado() == EstadoOrden.SUSPENDIDA) {
            compensarOrdenSuspendida(ordenDto, resultadoReserva.reservaId());
        } else {
            log.warn("Orden ID {}: Quedó en estado {} tras la evaluación climática.", ordenId, evaluada.getEstado());
        }
    }

    protected void compensarOrdenSuspendida(OrdenTrabajoDTO ordenDto, Long reservaId) {
        Long ordenId = ordenDto.getId();
        Long faseId = ordenDto.getFaseId();
        LocalDate nuevaFecha = ordenDto.getFechaProgramada().plusDays(1);

        // 1. Liberar la reserva
        if (reservaId != null) {
            liberarReservaRecursos(reservaId);
        }

        // 2. Reprogramar la fase en Obras
        reprogramarFaseEnObras(faseId, nuevaFecha);

        // 3. Crear la orden nueva
        if (!tieneOrdenHija(ordenDto)) {
            CrearOrdenRequest nuevaOrdenReq = new CrearOrdenRequest();
            nuevaOrdenReq.setFaseId(faseId);
            nuevaOrdenReq.setTramoId(ordenDto.getTramoId());
            nuevaOrdenReq.setFechaProgramada(nuevaFecha);
            otService.crearOrden(nuevaOrdenReq);
        }

        // 4. Pasar la vieja a REPROGRAMADA
        otService.cambiarEstado(ordenId, EstadoOrden.REPROGRAMADA);

        log.info("Orden ID {}: Compensación de suspensión completada -> REPROGRAMADA para {}.", ordenId, nuevaFecha);
    }

    private boolean tieneOrdenHija(OrdenTrabajoDTO ordenSuspendida) {
        LocalDate fechaHija = ordenSuspendida.getFechaProgramada().plusDays(1);
        List<OrdenTrabajo> ordenesFase = ordenTrabajoRepository.findByFaseId(ordenSuspendida.getFaseId());
        return ordenesFase.stream()
                .anyMatch(o -> !o.getId().equals(ordenSuspendida.getId())
                        && fechaHija.equals(o.getFechaProgramada()));
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
    private ResultadoReserva reservarRecursos(OrdenTrabajoDTO orden, DatosTramoFase datosObra) {
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
            Long reservaId = ((Number) resp.get("id")).longValue();
            LocalDateTime horaSalidaCamion = parsearHoraSalidaCamion(resp.get("horaSalidaCamion"), orden.getFechaProgramada());

            return new ResultadoReserva(reservaId, horaSalidaCamion);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                log.warn("Orden ID {}: Recursos rechazó la reserva: {}", orden.getId(), e.getResponseBodyAsString());
                return null;
            }
            throw e;
        }
    }

    private LocalDateTime parsearHoraSalidaCamion(Object valor, LocalDate fechaOrden) {
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(texto);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(texto).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.of(fechaOrden, LocalTime.parse(texto));
        } catch (Exception e) {
            log.warn("No se pudo parsear horaSalidaCamion '{}': {}", texto, e.getMessage());
            return null;
        }
    }

    private void confirmarReservaRecursos(Long reservaId) {
        sistemaRestClient.patch()
                .uri(recursosUrl + "/api/recursos/internos/reservas/" + reservaId + "/confirmacion")
                .retrieve()
                .toBodilessEntity();
    }

    private void liberarReservaRecursos(Long reservaId) {
        sistemaRestClient.patch()
                .uri(recursosUrl + "/api/recursos/internos/reservas/" + reservaId + "/liberacion")
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

    private record DatosTramoFase(Double latitud, Double longitud, TipoTrabajo tipoTrabajo, boolean faseAnteriorCompletada) {}
    private record ResultadoReserva(Long reservaId, LocalDateTime horaSalidaCamion) {}
}
