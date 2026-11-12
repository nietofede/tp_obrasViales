package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Llama a ms-obras como SISTEMA. El RestClient viene de obras-commons
// agrega el token para hablar con nuestros servicios
 @Slf4j
@Component
public class ObrasHttpClient implements ObrasClient {

    private static final String RUTA_CONTEXTO = "/api/obras/internos/fases/{id}/contexto-ejecucion";

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
}
