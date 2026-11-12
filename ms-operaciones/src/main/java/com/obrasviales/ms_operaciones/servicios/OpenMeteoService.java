package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OpenMeteoResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
public class OpenMeteoService implements InterfazClimaService {

    private final RestClient restClient;

    public OpenMeteoService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }

    @Override
    @Retry(name = "openMeteoRetry")
    @CircuitBreaker(name = "openMeteoCircuitBreaker", fallbackMethod = "obtenerCondicionesFallback")
    public DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud) {
        log.info("Consultando Open-Meteo para lat: {}, long: {}", latitud, longitud);

        OpenMeteoResponseDTO response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/forecast")
                        .queryParam("latitude", latitud)
                        .queryParam("longitude", longitud)
                        .queryParam("current", "temperature_2m,precipitation_probability")
                        .queryParam("hourly", "soil_moisture_0_to_1cm")
                        .queryParam("forecast_days", 1)
                        .build())
                .retrieve()
                .body(OpenMeteoResponseDTO.class);

        if (response == null || response.getCurrent() == null) {
            throw new RuntimeException("Respuesta vacía o inválida de Open-Meteo");
        }

        OpenMeteoResponseDTO.CurrentData current = response.getCurrent();
        Double humedadSueloActual = extraerHumedadSueloActual(response);

        return DatosClimaDTO.builder()
                .temperatura(current.getTemperature2m())
                .humedadSuelo(humedadSueloActual)
                .probabilidadLluvia(current.getPrecipitationProbability() != null ? current.getPrecipitationProbability() : 0.0)
                .build();
    }

    private Double extraerHumedadSueloActual(OpenMeteoResponseDTO response) {
        OpenMeteoResponseDTO.HourlyData hourly = response.getHourly();
        if (hourly == null || hourly.getSoilMoisture0To1cm() == null || hourly.getSoilMoisture0To1cm().isEmpty()) {
            return 0.0;
        }

        List<String> horas = hourly.getTime();
        List<Double> humedades = hourly.getSoilMoisture0To1cm();
        String tiempoActual = response.getCurrent().getTime();

        if (tiempoActual != null && tiempoActual.length() >= 13 && horas != null) {
            String horaPrefix = tiempoActual.substring(0, 13) + ":00";
            int indice = horas.indexOf(horaPrefix);
            if (indice >= 0 && indice < humedades.size() && humedades.get(indice) != null) {
                return humedades.get(indice);
            }
        }

        return humedades.get(0) != null ? humedades.get(0) : 0.0;
    }

    public DatosClimaDTO obtenerCondicionesFallback(Double latitud, Double longitud, Throwable t) {
        log.error("API de Open-Meteo no disponible para ({}, {}). Causa: {}", latitud, longitud, t.getMessage());
        throw new RuntimeException("No se pudo obtener el pronóstico climático en tiempo real. La orden debe pasar a evaluación manual.");
    }
}