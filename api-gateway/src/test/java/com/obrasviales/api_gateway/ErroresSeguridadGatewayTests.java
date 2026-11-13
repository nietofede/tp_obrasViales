package com.obrasviales.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Los 401 y 403 que decide el gateway salen en el formato estandar (application/problem+json), igual que los de
 * los servicios, con el mismo correlation-id que el header. Las rutas apuntan a un puerto sin uso: ningun pedido
 * llega a un servicio, lo que se mira es solo la respuesta del gateway.
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
class ErroresSeguridadGatewayTests {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void tokens() {
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> Jwt.withTokenValue(inv.getArgument(0))
                .header("alg", "none")
                .claim("sub", "operario")
                .claim("realm_access", Map.of("roles", List.of("OPERARIO")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());
        when(jwtDecoder.decode(eq("token-basura"))).thenThrow(new BadJwtException("firma invalida"));
    }

    private HttpResponse<String> get(String ruta, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).GET();
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void esProblemJson(HttpResponse<String> respuesta, int status, String type, String ruta) {
        assertThat(respuesta.statusCode()).isEqualTo(status);
        assertThat(respuesta.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        String id = respuesta.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(respuesta.body())
                .contains("\"type\":\"" + type + "\"")
                .contains("\"status\":" + status)
                .contains("\"instance\":\"" + ruta + "\"")
                .contains("\"correlationId\":\"" + id + "\"");
    }

    @Test
    void sinTokenDevuelve401EnFormatoEstandar() throws Exception {
        HttpResponse<String> respuesta = get("/api/obras", null);

        esProblemJson(respuesta, 401, "urn:problem:unauthorized", "/api/obras");
        assertThat(respuesta.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer");
    }

    @Test
    void conTokenInvalidoDevuelve401EnFormatoEstandar() throws Exception {
        HttpResponse<String> respuesta = get("/api/operaciones/ordenes", "token-basura");

        esProblemJson(respuesta, 401, "urn:problem:unauthorized", "/api/operaciones/ordenes");
        assertThat(respuesta.headers().firstValue("WWW-Authenticate").orElse("")).contains("invalid_token");
    }

    @Test
    void conRolInsuficienteDevuelve403EnFormatoEstandar() throws Exception {
        HttpResponse<String> respuesta = get("/api/operaciones/ordenes", "token-operario");

        esProblemJson(respuesta, 403, "urn:problem:forbidden", "/api/operaciones/ordenes");
    }
}
