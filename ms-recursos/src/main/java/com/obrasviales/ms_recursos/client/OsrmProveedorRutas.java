package com.obrasviales.ms_recursos.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OsrmProveedorRutas implements ProveedorRutas {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${osrm.base-url:https://router.project-osrm.org}")
    private String osrmBaseUrl;

    public record OsrmResponse(String code, OsrmRoute[] routes) {}
    public record OsrmRoute(double duration, double distance) {}

    @Override
    @Cacheable(value = "rutas", key = "{#origenLat, #origenLon, #destinoLat, #destinoLon}")
    @CircuitBreaker(name = "osrmBreaker", fallbackMethod = "fallbackCalcularRuta")
    @RateLimiter(name = "osrmLimiter", fallbackMethod = "fallbackCalcularRuta")
    public ResultadoRuta calcularRuta(double origenLat, double origenLon, double destinoLat, double destinoLon) {
        String url = osrmBaseUrl + "/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false";

        OsrmResponse response = restTemplate.getForObject(
                url, OsrmResponse.class, origenLon, origenLat, destinoLon, destinoLat
        );

        if (response != null && "Ok".equals(response.code())
                && response.routes() != null && response.routes().length > 0) {
            OsrmRoute route = response.routes()[0];
            int minutos = (int) Math.ceil(route.duration() / 60.0);
            double km = route.distance() / 1000.0;
            return new ResultadoRuta(minutos, km, "OSRM");
        }

        throw new RuntimeException("Respuesta inválida de OSRM");
    }

    // Método de Fallback invocado por CircuitBreaker / RateLimiter ante cualquier excepción
    public ResultadoRuta fallbackCalcularRuta(double origenLat, double origenLon,
                                             double destinoLat, double destinoLon,
                                             Throwable ex) {
        System.err.println("WARN [Resilience4j]: OSRM no disponible o limitado. Motivo: " 
                + ex.getMessage() + ". Usando estimación en línea recta.");

        double distanciaKm = calcularDistanciaHaversine(origenLat, origenLon, destinoLat, destinoLon);
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