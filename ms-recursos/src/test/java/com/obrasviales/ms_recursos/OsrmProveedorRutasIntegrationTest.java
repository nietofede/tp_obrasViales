package com.obrasviales.ms_recursos;

import com.obrasviales.ms_recursos.client.OsrmProveedorRutas;
import com.obrasviales.ms_recursos.client.ProveedorRutas;
import com.obrasviales.ms_recursos.client.ProveedorRutas.ResultadoRuta;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(classes = OsrmProveedorRutasIntegrationTest.TestConfig.class)
class OsrmProveedorRutasIntegrationTest {

    @Autowired
    private ProveedorRutas osrmProveedorRutas;

    @Autowired
    private CacheManager cacheManager;

    private MockRestServiceServer mockServer;

    @Configuration
    @EnableCaching
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(OsrmProveedorRutas.class)
    @ImportAutoConfiguration({CircuitBreakerAutoConfiguration.class, RateLimiterAutoConfiguration.class})
    static class TestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("rutas");
        }
    }

    @BeforeEach
    void setUp() {
        if (cacheManager.getCache("rutas") != null) {
            cacheManager.getCache("rutas").clear();
        }
        // Enlaza el mockServer al RestTemplate interno de OsrmProveedorRutas
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(osrmProveedorRutas, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("Debe devolver 'OSRM' cuando el servicio externo responde 200 OK")
    void testCalculoRutaExitoso() {
        String mockResponse = """
                {
                  "code": "Ok",
                  "routes": [
                    {
                      "duration": 600.0,
                      "distance": 10000.0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(startsWith("https://router.project-osrm.org/route/v1/driving/")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        ResultadoRuta resultado = osrmProveedorRutas.calcularRuta(-31.4250, -64.1900, -31.4200, -64.1800);

        assertNotNull(resultado);
        assertEquals("OSRM", resultado.fuente());
        assertEquals(10, resultado.duracionMinutos());
        assertEquals(10.0, resultado.distanciaKm(), 0.01);
        mockServer.verify();
    }

    @Test
    @DisplayName("Debe resolver desde Caché y no repetir la llamada HTTP ante idénticas coordenadas")
    void testCacheEvitaLlamadasRepetidas() {
        String mockResponse = """
                {
                  "code": "Ok",
                  "routes": [{ "duration": 300.0, "distance": 5000.0 }]
                }
                """;

        // Se espera exactamente UNA llamada HTTP
        mockServer.expect(once(), requestTo(startsWith("https://router.project-osrm.org/route/v1/driving/")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // Primera llamada
        osrmProveedorRutas.calcularRuta(-31.4000, -64.1500, -31.4100, -64.1600);
        // Segunda llamada (usa caché)
        osrmProveedorRutas.calcularRuta(-31.4000, -64.1500, -31.4100, -64.1600);

        mockServer.verify();
    }

    @Test
    @DisplayName("Debe activar Fallback ('ESTIMADA') cuando el servicio responde error 500")
    void testFallbackPorFallaDeServidor() {
        mockServer.expect(requestTo(startsWith("https://router.project-osrm.org/route/v1/driving/")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        ResultadoRuta resultado = osrmProveedorRutas.calcularRuta(-31.4250, -64.1900, -31.4200, -64.1800);

        assertNotNull(resultado);
        assertEquals("ESTIMADA", resultado.fuente());
        assertTrue(resultado.duracionMinutos() > 0);
        assertTrue(resultado.distanciaKm() > 0);
        mockServer.verify();
    }
}