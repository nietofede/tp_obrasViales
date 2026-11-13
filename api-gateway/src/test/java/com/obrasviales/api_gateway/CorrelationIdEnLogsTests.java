package com.obrasviales.api_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El patron logging.pattern.correlation del application.yml pone el correlation-id en cada linea de log
 * escrita durante el pedido. Se activa el debug del DispatcherServlet (corre dentro del filtro de correlacion)
 * para tener una linea de log de ese pedido y se busca en ella el mismo id que devolvio el header.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG")
class CorrelationIdEnLogsTests {

    @LocalServerPort
    private int port;

    @Test
    void elIdDelPedidoApareceEnElLog(CapturedOutput salida) throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String id = respuesta.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(salida.getOut()).contains("[" + id + "]");
    }
}
