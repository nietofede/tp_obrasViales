package com.obrasviales.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Reglas gruesas por ruta y metodo (SecurityConfig). El token "rol-X" se decodifica como un usuario con el rol X.
 * Las rutas apuntan a un puerto sin uso: un pedido que el gateway deja pasar falla al reenviarse (5xx), y uno
 * que rechaza da 403 sin llegar a ningun servicio. Lo que se mira es solo la decision del gateway.
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
class ReglasPorRolGatewayTests {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void tokensDePrueba() {
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            String rol = token.substring("rol-".length());
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", rol.toLowerCase())
                    .claim("realm_access", Map.of("roles", List.of(rol)))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        });
    }

    private int pedir(String metodo, String ruta, String rol) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
                .header("Authorization", "Bearer rol-" + rol)
                .header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString("{cuerpo invalido"))
                .build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @ParameterizedTest(name = "{1} {2} con {0} -> 403")
    @CsvSource({
            "OPERARIO,     GET,   /api/operaciones/ordenes",
            "OPERARIO,     GET,   /api/obras",
            "AUDITOR,      POST,  /api/operaciones/ordenes",
            "AUDITOR,      PATCH, /api/operaciones/ordenes/1/cancelacion",
            "ADMIN,        POST,  /api/operaciones/ordenes",
            "CAPATAZ,      PUT,   /api/operaciones/umbrales-climaticos/BASE",
            "PLANIFICADOR, PUT,   /api/operaciones/configuracion/limite-suspensiones",
            "AUDITOR,      PUT,   /api/obras/1",
            "CAPATAZ,      PATCH, /api/obras/fases/1/programacion",
            "SISTEMA,      GET,   /api/obras/internos/fases/1/contexto-ejecucion",
            "PLANIFICADOR, DELETE, /api/operaciones/ordenes/1"
    })
    void rechazaLosRolesQueNoTienenNadaQueHacerAhi(String rol, String metodo, String ruta) throws Exception {
        assertEquals(403, pedir(metodo, ruta, rol));
    }

    @ParameterizedTest(name = "{1} {2} con {0} pasa al servicio")
    @CsvSource({
            "PLANIFICADOR, POST,  /api/operaciones/ordenes",
            "AUDITOR,      GET,   /api/operaciones/ordenes",
            "ADMIN,        GET,   /api/operaciones/umbrales-climaticos",
            "ADMIN,        PUT,   /api/operaciones/umbrales-climaticos/BASE",
            "CAPATAZ,      PATCH, /api/operaciones/ordenes/1/inicio",
            "CAPATAZ,      POST,  /api/obras/fases/1/iniciar",
            "PLANIFICADOR, PATCH, /api/obras/fases/1/programacion",
            "OPERARIO,     GET,   /api/recursos/reservas/1"
    })
    void dejaPasarLosRolesQueAlgunEndpointAcepta(String rol, String metodo, String ruta) throws Exception {
        int estado = pedir(metodo, ruta, rol);
        assertNotEquals(401, estado);
        assertNotEquals(403, estado);
    }
}
