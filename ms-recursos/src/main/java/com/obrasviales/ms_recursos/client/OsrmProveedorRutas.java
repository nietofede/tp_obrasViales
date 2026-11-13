package com.obrasviales.ms_recursos.client;

import com.obrasviales.commons.dto.Coordenada;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

@Component
public class OsrmProveedorRutas implements ProveedorRutas {

    private static final Logger log = LoggerFactory.getLogger(OsrmProveedorRutas.class);

    private static final String USER_AGENT = "GOVP-ms-recursos/0.0.1 (TPI UTN FRC; Backend de Aplicaciones)";

    private final RestTemplate restTemplate;

    // Claves del application.yml (app.osrm.*). Sin valor por defecto: si falta la clave, la aplicación no arranca.

    @Value("${app.osrm.url}")
    private String osrmBaseUrl;

    // Multiplica los tiempos de OSRM (el perfil "driving" no considera el tránsito) y el desvío de la estimación en línea recta.
    @Value("${app.osrm.factor-correccion}")
    private double factorCorreccion;

    public OsrmProveedorRutas() {
        // Timeouts: sin ellos, una respuesta colgada de OSRM bloquearsi el hilo indefinidamente.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(fabrica);
        // El servidor de OSRM pide identificar al cliente.
        this.restTemplate.getInterceptors().add((request, body, ejecucion) -> {
            request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT);
            return ejecucion.execute(request, body);
        });
    }

    public record OsrmResponse(String code, OsrmRoute[] routes) {}
    public record OsrmRoute(double duration, double distance) {}

    @Override
    @Cacheable(value = "rutas", key = "{#origenLat, #origenLon, #destinoLat, #destinoLon}")
    @CircuitBreaker(name = "osrmBreaker", fallbackMethod = "fallbackCalcularRuta")
    @RateLimiter(name = "osrmLimiter", fallbackMethod = "fallbackCalcularRuta")
    public ResultadoRuta calcularRuta(double origenLat, double origenLon, double destinoLat, double destinoLon) {
        // Coordenada (obras-commons) valida los rangos y arma el par en el orden que espera OSRM: longitud,latitud
        Coordenada origen = new Coordenada(origenLat, origenLon);
        Coordenada destino = new Coordenada(destinoLat, destinoLon);
        String url = osrmBaseUrl + "/route/v1/driving/{origen};{destino}?overview=false";

        OsrmResponse response = restTemplate.getForObject(
                url, OsrmResponse.class, origen.toOsrmString(), destino.toOsrmString()
        );

        if (response != null && "Ok".equals(response.code())
                && response.routes() != null && response.routes().length > 0) {
            OsrmRoute route = response.routes()[0];
            int minutos = (int) Math.ceil(route.duration() / 60.0 * factorCorreccion);
            double km = route.distance() / 1000.0;
            return new ResultadoRuta(minutos, km, "OSRM");
        }

        throw new RuntimeException("Respuesta inválida de OSRM");
    }

    // Método de Fallback invocado por CircuitBreaker / RateLimiter ante cualquier excepción
    public ResultadoRuta fallbackCalcularRuta(double origenLat, double origenLon,
                                             double destinoLat, double destinoLon,
                                             Throwable ex) {
        log.warn("OSRM no disponible o limitado ({}). Se usa la estimación en línea recta.", ex.getMessage());

        double distanciaKm = calcularDistanciaHaversine(origenLat, origenLon, destinoLat, destinoLon) * factorCorreccion;
        int minutosEstimados = (int) Math.ceil((distanciaKm / 50.0) * 60.0);

        return new ResultadoRuta(Math.max(minutosEstimados, 5), distanciaKm, "ESTIMADA");
    }

    private double calcularDistanciaHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio de la tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}