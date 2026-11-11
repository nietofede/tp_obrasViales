package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OpenMeteoResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class OpenMeteoService implements InterfazClimaService {

    private final RestClient restClient;

    public OpenMeteoService() {
        // inicio restcliente con la url de Open-Meteo
        this.restClient = RestClient.builder()
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

    @Override
    @Retry(name = "openMeteoRetry")
    @CircuitBreaker(name = "openMeteoCircuitBreaker", fallbackMethod = "obtenerCondicionesFallback")
    public DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud) {
        log.info("Consultando Open-Meteo para lat: {}, long: {}", latitud, longitud);

        // pedimos a la api temperatura, humedad y probabilidad de precipitación
        OpenMeteoResponseDTO response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", latitud)
                        .queryParam("longitude", longitud)
                        .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation_probability")
                        .build())
                .retrieve()
                .body(OpenMeteoResponseDTO.class);

        if (response == null || response.getCurrent() == null) {
            throw new RuntimeException("Respuesta vacía o inválida de Open-Meteo");
        }

        OpenMeteoResponseDTO.CurrentData current = response.getCurrent();

        return DatosClimaDTO.builder()
                .temperatura(current.getTemperature2m())
                .humedadSuelo(current.getRelativeHumidity2m()) // Usamos la humedad relativa para la del suelo como aproximacion
                .probabilidadLluvia(current.getPrecipitationProbability() != null ? current.getPrecipitationProbability() : 0.0)
                .build();
    }

    // fallback por si llega a fallar la api
    public DatosClimaDTO obtenerCondicionesFallback(Double latitud, Double longitud, Throwable t) {
    log.error("API de Open-Meteo no disponible para ({}, {}). Causa: {}", latitud, longitud, t.getMessage());
    throw new RuntimeException("No se pudo obtener el pronóstico climático en tiempo real. La orden debe pasar a evaluación manual.");
}
}
