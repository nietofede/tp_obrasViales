package com.obrasviales.api_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pruebas de seguridad del gateway (SEG-01). No necesitan Postgres ni los servicios
 * de destino: solo verifican lo que decide el propio gateway antes de reenviar.
 *
 * Las rutas se redefinen apuntando a un puerto sin uso (9) para que ningun pedido
 * pueda llegar a un servicio real que este corriendo en la maquina: si llegara, un
 * 401 podria venir de la seguridad del servicio y no de la del gateway. Se redefinen
 * enteras (id, uri y predicado) porque Spring reemplaza la lista completa del
 * application.yml cuando otra fuente define algun indice de esa misma lista.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.server.webmvc.routes[0].id=ms-obras-route",
                "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:9",
                "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/api/obras/**",
                "spring.cloud.gateway.server.webmvc.routes[1].id=ms-recursos-route",
                "spring.cloud.gateway.server.webmvc.routes[1].uri=http://localhost:9",
                "spring.cloud.gateway.server.webmvc.routes[1].predicates[0]=Path=/api/recursos/**",
                "spring.cloud.gateway.server.webmvc.routes[2].id=ms-operaciones-route",
                "spring.cloud.gateway.server.webmvc.routes[2].uri=http://localhost:9",
                "spring.cloud.gateway.server.webmvc.routes[2].predicates[0]=Path=/api/operaciones/**"
        })
class SeguridadGatewayTests {

    private static final String HEADER = "X-Correlation-Id";

    @LocalServerPort
    private int port;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> get(String ruta, String... headers) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).GET();
        if (headers.length > 0) {
            pedido.headers(headers);
        }
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/obras/x", "/api/recursos/x", "/api/operaciones/x"})
    void rutaProtegidaSinTokenDevuelve401(String ruta) throws Exception {
        assertEquals(401, get(ruta).statusCode());
    }

    @Test
    void healthEstaAbiertoSinToken() throws Exception {
        assertEquals(200, get("/actuator/health").statusCode());
    }

    @Test
    void ignoraElCorrelationIdDelClienteYGeneraUnoNuevo() throws Exception {
        HttpResponse<String> respuesta = get("/api/obras/x", HEADER, "MIO-123");

        String id = respuesta.headers().firstValue(HEADER).orElseThrow();
        assertNotEquals("MIO-123", id);
        assertDoesNotThrow(() -> UUID.fromString(id));
    }

    @Test
    void cadaPedidoRecibeUnCorrelationIdDistinto() throws Exception {
        String primero = get("/api/obras/x").headers().firstValue(HEADER).orElseThrow();
        String segundo = get("/api/obras/x").headers().firstValue(HEADER).orElseThrow();

        assertNotEquals(primero, segundo);
    }

    @Test
    void laRespuestaTraeUnSoloCorrelationId() throws Exception {
        assertEquals(1, get("/actuator/health").headers().allValues(HEADER).size());
    }
}
