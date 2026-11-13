package com.obrasviales.commons.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Va en ms-operaciones porque obras-commons todavia no tiene dependencias de test; se mueve cuando las tenga.
// Keycloak simulado con MockRestServiceServer y una espera falsa, para no dormir en los tests.
class SistemaTokenServiceTest {

    private static final String TOKEN_URL = "http://keycloak.test/realms/obras-viales/protocol/openid-connect/token";
    private static final String TOKEN_OK = "{\"access_token\":\"abc\",\"expires_in\":300}";

    private MockRestServiceServer keycloak;
    private SistemaTokenService service;
    private final List<Duration> esperas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        SistemaClientProperties properties = new SistemaClientProperties();
        properties.setTokenUrl(TOKEN_URL);
        properties.setClientSecret("secreto");
        service = new SistemaTokenService(properties, builder.build(), esperas::add);
    }

    @Test
    void obtieneElTokenYLoReutilizaMientrasNoVence() {
        keycloak.expect(once(), requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));

        assertThat(service.obtenerToken()).isEqualTo("abc");
        assertThat(service.obtenerToken()).isEqualTo("abc");
        keycloak.verify();
        assertThat(esperas).isEmpty();
    }

    @Test
    void siKeycloakNoRespondeReintentaYLuegoObtieneElToken() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withException(new IOException("Connection refused")));
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withServerError());
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));

        assertThat(service.obtenerToken()).isEqualTo("abc");
        keycloak.verify();
        assertThat(esperas).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    void despuesDeTresIntentosFallidosPropagaElError() {
        keycloak.expect(times(SistemaTokenService.MAX_INTENTOS), requestTo(TOKEN_URL))
                .andRespond(withException(new IOException("Connection refused")));

        assertThrows(ResourceAccessException.class, () -> service.obtenerToken());
        keycloak.verify();
        assertThat(esperas).hasSize(SistemaTokenService.MAX_INTENTOS - 1);
    }

    @Test
    void conCredencialesInvalidasNoReintenta() {
        keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.class, () -> service.obtenerToken());
        keycloak.verify();
        assertThat(esperas).isEmpty();
    }
}
