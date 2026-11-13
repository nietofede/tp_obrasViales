package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.dto.Coordenada;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenMeteoService implements InterfazClimaService {

    private static final Duration TTL_POR_DEFECTO = Duration.ofMinutes(15);
    private static final int VENTANA_POR_DEFECTO = 6;
    private static final int HORA_INICIO_POR_DEFECTO = 8;

    private final RestClient restClient;
    private final String url;
    private final Duration ttl;
    private final Clock reloj;
    // ventana de ejecucion: desde la hora de inicio de la jornada, tantas horas como diga la configuracion
    private final int ventanaHoras;
    private final int horaInicio;

    // ultimo pronostico valido por coordenada: el job consulta varias ordenes del mismo tramo seguidas
    private final Map<String, PronosticoGuardado> cache = new ConcurrentHashMap<>();

    // La URL sale de app.open-meteo.url, tiene timeout
    @Autowired
    public OpenMeteoService(@Value("${app.open-meteo.url}") String url,
                            @Value("${app.open-meteo.cache-ttl:PT15M}") Duration ttl,
                            @Value("${app.open-meteo.ventana-ejecucion-horas:6}") int ventanaHoras,
                            @Value("${app.open-meteo.hora-inicio-jornada:8}") int horaInicio) {
        this(construirRestClient(), url, ttl, Clock.systemDefaultZone(), ventanaHoras, horaInicio);
    }

    // Para los tests (RestClient simulado).
    OpenMeteoService(RestClient restClient, String url) {
        this(restClient, url, TTL_POR_DEFECTO, Clock.systemDefaultZone());
    }

    OpenMeteoService(RestClient restClient, String url, Duration ttl, Clock reloj) {
        this(restClient, url, ttl, reloj, VENTANA_POR_DEFECTO, HORA_INICIO_POR_DEFECTO);
    }

    OpenMeteoService(RestClient restClient, String url, Duration ttl, Clock reloj, int ventanaHoras, int horaInicio) {
        if (ventanaHoras < 1 || horaInicio < 0 || horaInicio + ventanaHoras > 24) {
            throw new IllegalArgumentException("La ventana de ejecución tiene que caer dentro del día: inicio "
                    + horaInicio + " h y " + ventanaHoras + " h de duración.");
        }
        this.restClient = restClient;
        this.url = url;
        this.ttl = ttl;
        this.reloj = reloj;
        this.ventanaHoras = ventanaHoras;
        this.horaInicio = horaInicio;
    }

    private static RestClient construirRestClient() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    // RN-09: el clima que importa es el de la jornada de la orden, no el de este momento. Se piden los datos
    // horarios de la fecha de la orden y se toma el PEOR valor de la ventana de ejecucion (la mayor humedad del
    // suelo, la mayor probabilidad de lluvia y la menor temperatura): si una sola hora no cumple, no se habilita.
    @Override
    @Retry(name = "openMeteoRetry")
    @CircuitBreaker(name = "openMeteoCircuitBreaker", fallbackMethod = "obtenerCondicionesFallback")
    public DatosClimaDTO obtenerCondicionesClimaticas(Double latitud, Double longitud, LocalDate fecha) {
        String clave = claveDe(latitud, longitud, fecha);
        Instant ahora = reloj.instant();
        PronosticoGuardado guardado = cache.get(clave);
        if (guardado != null && ahora.isBefore(guardado.obtenido().plus(ttl))) {
            log.info("Pronóstico de Open-Meteo en caché para lat: {}, long: {}, fecha: {}", latitud, longitud, fecha);
            return guardado.datos();
        }

        DatosClimaDTO datos = consultarOpenMeteo(latitud, longitud, fecha);
        // solo se guarda una respuesta completa: si falta un dato se lanza error antes y no se inventa nada (RN-10)
        cache.put(clave, new PronosticoGuardado(datos, ahora));
        return datos;
    }

    // ~100 m de precision: dos ordenes del mismo tramo y la misma fecha comparten el pronostico
    private static String claveDe(Double latitud, Double longitud, LocalDate fecha) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%s", latitud, longitud, fecha);
    }

    private record PronosticoGuardado(DatosClimaDTO datos, Instant obtenido) {
    }

    private DatosClimaDTO consultarOpenMeteo(Double latitud, Double longitud, LocalDate fecha) {
        log.info("Consultando Open-Meteo para lat: {}, long: {}, fecha: {}", latitud, longitud, fecha);

        // Coordenada (obras-commons) rechaza una latitud o longitud fuera de rango antes de llamar a la API
        Coordenada tramo = new Coordenada(latitud, longitud);
        // timezone=auto: las horas vienen en la hora local del tramo, igual que la hora de inicio de la jornada
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", tramo.latitud())
                .queryParam("longitude", tramo.longitud())
                .queryParam("hourly", "temperature_2m,precipitation_probability,soil_moisture_0_to_1cm")
                .queryParam("timezone", "auto")
                .queryParam("start_date", fecha)
                .queryParam("end_date", fecha)
                .build()
                .toUri();

        OpenMeteoResponseDTO response = restClient.get().uri(uri).retrieve().body(OpenMeteoResponseDTO.class);

        if (response == null || response.getHourly() == null || response.getHourly().getTime() == null) {
            throw new IllegalStateException("Respuesta vacía o inválida de Open-Meteo");
        }

        // si falta cualquier dato de la ventana NO se asume un valor:
        // se lanza error y la orden pasa a revision manual (RN-10)
        List<Integer> horas = indicesDeLaVentana(response.getHourly().getTime(), fecha);
        OpenMeteoResponseDTO.HourlyData hourly = response.getHourly();
        double temperaturaMinima = Double.MAX_VALUE;
        double lluviaMaxima = -1;
        double humedadMaxima = -1;
        for (int i : horas) {
            temperaturaMinima = Math.min(temperaturaMinima, valorEn(hourly.getTemperature2m(), i, "la temperatura"));
            lluviaMaxima = Math.max(lluviaMaxima, valorEn(hourly.getPrecipitationProbability(), i, "la probabilidad de lluvia"));
            humedadMaxima = Math.max(humedadMaxima, valorEn(hourly.getSoilMoisture0To1cm(), i, "la humedad del suelo"));
        }

        return DatosClimaDTO.builder()
                .temperatura(temperaturaMinima)
                .humedadSuelo(humedadMaxima)
                .probabilidadLluvia(lluviaMaxima)
                .build();
    }

    // posiciones, en los datos horarios, de cada hora de la ventana (por ejemplo de 08:00 a 13:00 para 6 horas)
    private List<Integer> indicesDeLaVentana(List<String> tiempos, LocalDate fecha) {
        List<Integer> indices = new ArrayList<>();
        for (int h = horaInicio; h < horaInicio + ventanaHoras; h++) {
            String buscada = String.format(Locale.ROOT, "%sT%02d:00", fecha, h);
            int indice = tiempos.indexOf(buscada);
            if (indice < 0) {
                throw new IllegalStateException("Open-Meteo no devolvió el pronóstico de " + buscada + ".");
            }
            indices.add(indice);
        }
        return indices;
    }

    private Double valorEn(List<Double> valores, int indice, String nombre) {
        if (valores == null || indice >= valores.size() || valores.get(indice) == null) {
            throw new IllegalStateException("Open-Meteo no devolvió " + nombre + ".");
        }
        return valores.get(indice);
    }

    public DatosClimaDTO obtenerCondicionesFallback(Double latitud, Double longitud, LocalDate fecha, Throwable t) {
        log.error("API de Open-Meteo no disponible para ({}, {}) el {}. Causa: {}", latitud, longitud, fecha, t.getMessage());
        throw new RuntimeException("No se pudo obtener el pronóstico climático en tiempo real. La orden debe pasar a evaluación manual.");
    }
}
