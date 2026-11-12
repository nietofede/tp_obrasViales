package com.obrasviales.ms_operaciones.clientes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
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
import java.util.Map;
import java.util.function.Supplier;

// Llama a ms-obras como SISTEMA. El RestClient viene de obras-commons
// agrega el token para hablar con nuestros servicios
 @Slf4j
@Component
public class ObrasHttpClient implements ObrasClient {

    private static final String RUTA_CONTEXTO = "/api/obras/internos/fases/{id}/contexto-ejecucion";
    private static final String RUTA_PROGRAMACION = "/api/obras/fases/{id}/programacion";
    private static final String RUTA_RIESGO = "/api/obras/internos/fases/{id}/riesgo";

    private final RestClient restClient;
    private final String obrasUrl;

    public ObrasHttpClient(@Qualifier("sistemaRestClient") RestClient restClient,
                           @Value("${app.servicios.obras-url}") String obrasUrl) {
        this.restClient = restClient;
        this.obrasUrl = obrasUrl;
    }

    @Override
    public ContextoFaseDTO obtenerContextoFase(Long faseId) {
        try {
            ContextoFaseDTO contexto = restClient.get()
                    .uri(obrasUrl + RUTA_CONTEXTO, faseId)
                    .retrieve()
                    .body(ContextoFaseDTO.class);
            if (contexto == null) {
                throw new ServicioNoDisponibleException("ms-obras devolvió una respuesta vacía.");
            }
            return contexto;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecursoNoEncontradoException("La fase " + faseId + " no existe en ms-obras.");
        } catch (RestClientException e) {
            log.error("Falló la llamada a ms-obras para la fase {}: {}", faseId, e.getMessage());
            throw new ServicioNoDisponibleException("No se pudo consultar a ms-obras.");
        }
    }

    @Override
    public FaseDTO reprogramarFase(Long faseId, LocalDate nuevaFechaInicio) {
        return ejecutar("reprogramar la fase " + faseId, () -> restClient.patch()
                .uri(obrasUrl + RUTA_PROGRAMACION, faseId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nuevaFechaInicio", nuevaFechaInicio.toString()))
                .retrieve()
                .body(FaseDTO.class));
    }

    @Override
    public FaseDTO marcarFaseEnRiesgo(Long faseId) {
        return ejecutar("marcar en riesgo la fase " + faseId, () -> restClient.patch()
                .uri(obrasUrl + RUTA_RIESGO, faseId)
                .retrieve()
                .body(FaseDTO.class));
    }

    private FaseDTO ejecutar(String descripcion, Supplier<FaseDTO> llamada) {
        try {
            FaseDTO fase = llamada.get();
            if (fase == null) {
                throw new ServicioNoDisponibleException("ms-obras devolvió una respuesta vacía.");
            }
            return fase;
        } catch (HttpClientErrorException e) {
            throw traducir(e, descripcion);
        } catch (RestClientException e) {
            log.error("Falló la llamada a ms-obras al {}: {}", descripcion, e.getMessage());
            throw new ServicioNoDisponibleException("No se pudo consultar a ms-obras.");
        }
    }

    // Rechazos de negocio de Obras (422 con RN-02 u otro codigo, 409 de estado, 404) se reenvian con su motivo.
    private RuntimeException traducir(HttpClientErrorException e, String descripcion) {
        ErrorObras error;
        try {
            error = e.getResponseBodyAs(ErrorObras.class);
        } catch (RuntimeException ex) {
            error = null;
        }
        int status = e.getStatusCode().value();
        String detalle = (error != null && error.detail() != null)
                ? error.detail()
                : "ms-obras rechazó la solicitud (" + status + ").";
        return switch (status) {
            case 422 -> new ReglaNegocioException(detalle,
                    (error != null && error.codigoRegla() != null) ? error.codigoRegla() : "OBRAS");
            case 409 -> new ConflictoEstadoException(detalle);
            case 404 -> new RecursoNoEncontradoException(detalle);
            default -> {
                log.error("ms-obras respondió {} al {}: {}", status, descripcion, detalle);
                yield new ServicioNoDisponibleException("No se pudo consultar a ms-obras.");
            }
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorObras(String detail, String codigoRegla) {
    }
}
