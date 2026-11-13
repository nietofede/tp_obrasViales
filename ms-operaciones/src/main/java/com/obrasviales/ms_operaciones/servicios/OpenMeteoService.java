package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OpenMeteoResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class OpenMeteoService implements InterfazClimaService {

    private final RestClient restClient;
    private final String url;

    // La URL sale de app.open-meteo.url, tiene timeout
    @Autowired
    public OpenMeteoService(@Value("${app.open-meteo.url}") String url) {
        this(construirRestClient(), url);
    }

    // Para los tests (RestClient simulado).
    OpenMeteoService(RestClient restClient, String url) {
        this.restClient = restClient;
        this.url = url;
    }

    private static RestClient construirRestClient() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    @Retry(name = "openMeteoRetry")
    @CircuitBreaker(name = "openMeteoCircuitBreaker", fallbackMethod = "obtenerCondicionesFallback")
    public DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud) {
        log.info("Consultando Open-Meteo para lat: {}, long: {}", latitud, longitud);

        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", latitud)
                .queryParam("longitude", longitud)
                .queryParam("current", "temperature_2m")
                .queryParam("hourly", "precipitation_probability,soil_moisture_0_to_1cm")
                .queryParam("forecast_days", 1)
                .build()
                .toUri();

        OpenMeteoResponseDTO response = restClient.get().uri(uri).retrieve().body(OpenMeteoResponseDTO.class);

        if (response == null || response.getCurrent() == null || response.getHourly() == null) {
            throw new IllegalStateException("Respuesta vacía o inválida de Open-Meteo");
        }

        // si falta cualquiera de los tres datos NO se asume un valor 
        // Se lanza error y la orden pasa a revision manual
        Double temperatura = response.getCurrent().getTemperature2m();
        if (temperatura == null) {
            throw new IllegalStateException("Open-Meteo no devolvió la temperatura.");
        }
        int hora = indiceHoraActual(response);
        Double probabilidadLluvia = valorEn(response.getHourly().getPrecipitationProbability(), hora, "probabilidad de lluvia");
        Double humedadSuelo = valorEn(response.getHourly().getSoilMoisture0To1cm(), hora, "humedad del suelo");

        return DatosClimaDTO.builder()
                .temperatura(temperatura)
                .humedadSuelo(humedadSuelo)
                .probabilidadLluvia(probabilidadLluvia)
                .build();
    }

    // posicion en los datos horarios de la hora actual 
    private int indiceHoraActual(OpenMeteoResponseDTO response) {
        String actual = response.getCurrent().getTime();
        List<String> horas = response.getHourly().getTime();
        if (actual == null || actual.length() < 13 || horas == null) {
            throw new IllegalStateException("Open-Meteo no devolvió la hora de los datos.");
        }
        int indice = horas.indexOf(actual.substring(0, 13) + ":00");
        if (indice < 0) {
            throw new IllegalStateException("Open-Meteo no devolvió datos para la hora actual.");
        }
        return indice;
    }

    private Double valorEn(List<Double> valores, int indice, String nombre) {
        if (valores == null || indice >= valores.size() || valores.get(indice) == null) {
            throw new IllegalStateException("Open-Meteo no devolvió " + nombre + ".");
        }
        return valores.get(indice);
    }

    public DatosClimaDTO obtenerCondicionesFallback(Double latitud, Double longitud, Throwable t) {
        log.error("API de Open-Meteo no disponible para ({}, {}). Causa: {}", latitud, longitud, t.getMessage());
        throw new RuntimeException("No se pudo obtener el pronóstico climático en tiempo real. La orden debe pasar a evaluación manual.");
    }
}
