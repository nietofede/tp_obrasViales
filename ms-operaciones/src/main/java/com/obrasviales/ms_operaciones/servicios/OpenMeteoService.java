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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenMeteoService implements InterfazClimaService {

    private static final Duration TTL_POR_DEFECTO = Duration.ofMinutes(15);

    private final RestClient restClient;
    private final String url;
    private final Duration ttl;
    private final Clock reloj;

    // ultimo pronostico valido por coordenada: el job consulta varias ordenes del mismo tramo seguidas
    private final Map<String, PronosticoGuardado> cache = new ConcurrentHashMap<>();

    // La URL sale de app.open-meteo.url, tiene timeout
    @Autowired
    public OpenMeteoService(@Value("${app.open-meteo.url}") String url,
                            @Value("${app.open-meteo.cache-ttl:PT15M}") Duration ttl) {
        this(construirRestClient(), url, ttl, Clock.systemDefaultZone());
    }

    // Para los tests (RestClient simulado).
    OpenMeteoService(RestClient restClient, String url) {
        this(restClient, url, TTL_POR_DEFECTO, Clock.systemDefaultZone());
    }

    OpenMeteoService(RestClient restClient, String url, Duration ttl, Clock reloj) {
        this.restClient = restClient;
        this.url = url;
        this.ttl = ttl;
        this.reloj = reloj;
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
        String clave = claveDe(latitud, longitud);
        Instant ahora = reloj.instant();
        PronosticoGuardado guardado = cache.get(clave);
        if (guardado != null && ahora.isBefore(guardado.obtenido().plus(ttl))) {
            log.info("Pronóstico de Open-Meteo en caché para lat: {}, long: {}", latitud, longitud);
            return guardado.datos();
        }

        DatosClimaDTO datos = consultarOpenMeteo(latitud, longitud);
        // solo se guarda una respuesta completa: si falta un dato se lanza error antes y no se inventa nada (RN-10)
        cache.put(clave, new PronosticoGuardado(datos, ahora));
        return datos;
    }

    // ~100 m de precision: dos ordenes del mismo tramo comparten el pronostico
    private static String claveDe(Double latitud, Double longitud) {
        return String.format(Locale.ROOT, "%.3f,%.3f", latitud, longitud);
    }

    private record PronosticoGuardado(DatosClimaDTO datos, Instant obtenido) {
    }

    private DatosClimaDTO consultarOpenMeteo(Double latitud, Double longitud) {
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
