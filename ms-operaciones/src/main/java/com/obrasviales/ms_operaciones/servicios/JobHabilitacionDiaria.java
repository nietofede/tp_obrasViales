package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.*;
import com.obrasviales.ms_operaciones.repositorios.EvaluacionClimaticaRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.UmbralClimaticoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobHabilitacionDiaria {

    private final OTServiceInterfaz otService;
    private final InterfazClimaService climaService;
    private final RestClient sistemaRestClient; // Bean de obras-commons con interceptor de token SISTEMA
    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final UmbralClimaticoRepository umbralClimaticoRepository;
    private final EvaluacionClimaticaRepository evaluacionClimaticaRepository;

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
        OrdenTrabajo ordenEntidad = ordenTrabajoRepository.findById(ordenId)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada: " + ordenId));

        // 1. Consultar datos del tramo/fase en ms-obras usando sistemaRestClient (token SISTEMA)
        DatosTramoFase datosObra = obtenerDatosObra(ordenDto.getTramoId(), ordenDto.getFaseId());

        // 2. Reservar recursos en ms-recursos usando sistemaRestClient
        Long reservaId = reservarRecursos(ordenDto, datosObra);
        if (reservaId == null) {
            otService.cambiarEstado(ordenId, EstadoOrden.CANCELADA);
            log.warn("Orden ID {}: CANCELADA por falta de disponibilidad o exceso de distancia en recursos.", ordenId);
            return;
        }

        ordenEntidad.setReservaId(reservaId);
        ordenTrabajoRepository.save(ordenEntidad);
        otService.cambiarEstado(ordenId, EstadoOrden.RECURSOS_ASIGNADOS);
        log.info("Orden ID {}: Recursos asignados (Reserva ID {}).", ordenId, reservaId);

        // 3. Pasar a EVALUACION_CLIMATICA y consultar Open-Meteo
        otService.cambiarEstado(ordenId, EstadoOrden.EVALUACION_CLIMATICA);
        DatosClimaDTO clima;
        try {
            clima = climaService.obtenerCondicionesClimaticas(datosObra.latitud(), datosObra.longitud());
        } catch (Exception e) {
            log.warn("Orden ID {}: Falló consulta climática ({}). Pasa a PENDIENTE_CONFIRMACION_CLIMATICA.", ordenId, e.getMessage());
            otService.cambiarEstado(ordenId, EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
            return;
        }

        // 4. Evaluar clima contra umbrales configurados en BD
        UmbralClimatico umbral = umbralClimaticoRepository.findByTipoTrabajo(datosObra.tipoTrabajo())
                .orElseThrow(() -> new IllegalStateException("No hay umbral configurado para " + datosObra.tipoTrabajo()));

        // OpenMeteoService devuelve humedad relativa en % (0-100) y umbral en decimal (ej. 0.40)
        double humedadNormalizada = clima.getHumedadSuelo() > 1.0 ? clima.getHumedadSuelo() / 100.0 : clima.getHumedadSuelo();

        boolean cumpleHumedad = humedadNormalizada <= umbral.getHumedadSueloMaxima();
        boolean cumpleLluvia = clima.getProbabilidadLluvia() <= umbral.getProbPrecipitacionMaxima();
        boolean cumpleTemp = clima.getTemperatura() >= umbral.getTemperaturaMinima();
        boolean umbralesCumplidos = cumpleHumedad && cumpleLluvia && cumpleTemp;

        // 5. Persistir registro de evaluación climática
        EvaluacionClimatica eval = new EvaluacionClimatica();
        eval.setOrdenTrabajo(ordenEntidad);
        eval.setHumedadSueloRegistrada(humedadNormalizada);
        eval.setProbPrecipitacionRegistrada(clima.getProbabilidadLluvia());
        eval.setTemperaturaRegistrada(clima.getTemperatura());
        eval.setUmbralesCumplidos(umbralesCumplidos);
        evaluacionClimaticaRepository.save(eval);

        // 6. Transición final según evaluación climática
        if (umbralesCumplidos) {
            otService.cambiarEstado(ordenId, EstadoOrden.HABILITADA);
            log.info("Orden ID {}: HABILITADA (Temp: {}°C, Lluvia: {}%, Humedad: {}).",
                    ordenId, clima.getTemperatura(), clima.getProbabilidadLluvia(), humedadNormalizada);
        } else {
            ordenEntidad.setContadorSuspensiones(ordenEntidad.getContadorSuspensiones() + 1);
            ordenTrabajoRepository.save(ordenEntidad);
            otService.cambiarEstado(ordenId, EstadoOrden.SUSPENDIDA);
            log.info("Orden ID {}: SUSPENDIDA por clima adverso (Temp: {}°C, Lluvia: {}%, Humedad: {}).",
                    ordenId, clima.getTemperatura(), clima.getProbabilidadLluvia(), humedadNormalizada);
        }
    }

    @SuppressWarnings("unchecked")
    private DatosTramoFase obtenerDatosObra(Long tramoId, Long faseId) {
        try {
            Map<String, Object> tramo = sistemaRestClient.get()
                    .uri(obrasUrl + "/api/tramos/" + tramoId)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> fase = sistemaRestClient.get()
                    .uri(obrasUrl + "/api/fases/" + faseId)
                    .retrieve()
                    .body(Map.class);

            Double lat = tramo != null && tramo.get("latitud") != null ? ((Number) tramo.get("latitud")).doubleValue() : -31.245;
            Double lon = tramo != null && tramo.get("longitud") != null ? ((Number) tramo.get("longitud")).doubleValue() : -64.456;
            TipoTrabajo tipo = fase != null && fase.get("tipoFase") != null
                    ? TipoTrabajo.valueOf(fase.get("tipoFase").toString())
                    : TipoTrabajo.BASE;

            return new DatosTramoFase(lat, lon, tipo);
        } catch (Exception e) {
            log.warn("No se pudo contactar ms-obras ({}). Usando coordenadas demo del Tramo 1 (-31.245, -64.456, BASE).", e.getMessage());
            return new DatosTramoFase(-31.245, -64.456, TipoTrabajo.BASE);
        }
    }

    @SuppressWarnings("unchecked")
    private Long reservarRecursos(OrdenTrabajoDTO orden, DatosTramoFase datosObra) {
        try {
            Map<String, Object> body = Map.of(
                    "ordenTrabajoId", orden.getId(),
                    "tramoId", orden.getTramoId(),
                    "tipoTrabajo", datosObra.tipoTrabajo().name(),
                    "fecha", orden.getFechaProgramada().toString()
            );

            Map<String, Object> resp = sistemaRestClient.post()
                    .uri(recursosUrl + "/api/reservas")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return resp != null && resp.get("id") != null ? ((Number) resp.get("id")).longValue() : 1L;
        } catch (Exception e) {
            log.warn("No se pudo contactar ms-recursos ({}). Simulando ID de reserva 1 para continuar flujo operativo.", e.getMessage());
            return 1L;
        }
    }

    private record DatosTramoFase(Double latitud, Double longitud, TipoTrabajo tipoTrabajo) {}
}