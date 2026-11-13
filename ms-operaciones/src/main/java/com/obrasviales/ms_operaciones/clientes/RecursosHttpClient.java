package com.obrasviales.ms_operaciones.clientes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.dtos.ReservaDTO;
import com.obrasviales.ms_operaciones.dtos.SolicitudReservaDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.function.Supplier;

// Llama a ms-recursos con la cuenta SISTEMA 
@Slf4j
@Component
public class RecursosHttpClient implements RecursosClient {

    private static final String RUTA_RESERVAS = "/api/recursos/internos/reservas";
    private static final String RUTA_CONFIRMACION = RUTA_RESERVAS + "/{id}/confirmacion";
    private static final String RUTA_LIBERACION = RUTA_RESERVAS + "/{id}/liberacion";

    private final RestClient restClient;
    private final String recursosUrl;

    public RecursosHttpClient(@Qualifier("sistemaRestClient") RestClient restClient,
                              @Value("${app.servicios.recursos-url}") String recursosUrl) {
        this.restClient = restClient;
        this.recursosUrl = recursosUrl;
    }

    @Override
    public ReservaDTO proponerYReservar(Long ordenTrabajoId, String tipoTrabajo, LocalDate fecha,
                                        Double tramoLat, Double tramoLon) {
        SolicitudReservaDTO solicitud =
                new SolicitudReservaDTO(ordenTrabajoId, tipoTrabajo, fecha.toString(), tramoLat, tramoLon);
        return ejecutar("reservar recursos para la orden " + ordenTrabajoId, () -> restClient.post()
                .uri(recursosUrl + RUTA_RESERVAS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .body(ReservaDTO.class));
    }

    @Override
    public ReservaDTO confirmar(Long reservaId) {
        return ejecutar("confirmar la reserva " + reservaId, () -> restClient.patch()
                .uri(recursosUrl + RUTA_CONFIRMACION, reservaId)
                .retrieve()
                .body(ReservaDTO.class));
    }

    @Override
    public ReservaDTO liberar(Long reservaId) {
        return ejecutar("liberar la reserva " + reservaId, () -> restClient.patch()
                .uri(recursosUrl + RUTA_LIBERACION, reservaId)
                .retrieve()
                .body(ReservaDTO.class));
    }

    private ReservaDTO ejecutar(String descripcion, Supplier<ReservaDTO> llamada) {
        try {
            ReservaDTO reserva = llamada.get();
            if (reserva == null) {
                throw new ServicioNoDisponibleException("ms-recursos devolvió una respuesta vacía.");
            }
            return reserva;
        } catch (HttpClientErrorException e) {
            throw traducir(e, descripcion);
        } catch (RestClientException e) {
            log.error("Falló la llamada a ms-recursos al {}: {}", descripcion, e.getMessage());
            throw new ServicioNoDisponibleException("No se pudo consultar a ms-recursos.");
        }
    }

    private RuntimeException traducir(HttpClientErrorException e, String descripcion) {
        ErrorRecursos error = leerError(e);
        int status = e.getStatusCode().value();
        String detalle = (error != null && error.detail() != null)
                ? error.detail()
                : "ms-recursos rechazó la solicitud (" + status + ").";
        return switch (status) {
            case 422 -> new ReglaNegocioException(detalle,
                    (error != null && error.codigoRegla() != null) ? error.codigoRegla() : "RECURSOS");
            case 409 -> new ConflictoEstadoException(detalle);
            case 404 -> new RecursoNoEncontradoException(detalle);
            default -> {
                log.error("ms-recursos respondió {} al {}: {}", status, descripcion, detalle);
                yield new ServicioNoDisponibleException("No se pudo consultar a ms-recursos.");
            }
        };
    }

    private ErrorRecursos leerError(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(ErrorRecursos.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ProblemDetail de Recursos
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorRecursos(String detail, String codigoRegla) {
    }
}
