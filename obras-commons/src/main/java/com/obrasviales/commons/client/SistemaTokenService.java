package com.obrasviales.commons.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class SistemaTokenService {

    private static final Logger log = LoggerFactory.getLogger(SistemaTokenService.class);

    // Keycloak puede tardar en estar listo al arrancar el stack: se reintenta antes de dar el error.
    static final int MAX_INTENTOS = 3;
    private static final Duration ESPERA_INICIAL = Duration.ofMillis(500);

    private final SistemaClientProperties properties;
    private final RestClient restClient;
    private final Espera espera;

    private String cachedToken;
    private Instant expiresAt = Instant.MIN;

    @Autowired
    public SistemaTokenService(SistemaClientProperties properties) {
        this(properties, clienteConTimeouts(), duracion -> Thread.sleep(duracion.toMillis()));
    }

    SistemaTokenService(SistemaClientProperties properties, RestClient restClient, Espera espera) {
        this.properties = properties;
        this.restClient = restClient;
        this.espera = espera;
    }

    // sin timeouts, un Keycloak colgado dejaba colgado al servicio que pedia el token
    private static RestClient clienteConTimeouts() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    public synchronized String obtenerToken() {
        // Renueva el token si no existe o si faltan 30 segundos para vencer
        if (cachedToken == null || Instant.now().plusSeconds(30).isAfter(expiresAt)) {
            solicitarConReintentos();
        }
        return cachedToken;
    }

    private void solicitarConReintentos() {
        Duration pausa = ESPERA_INICIAL;
        for (int intento = 1; ; intento++) {
            try {
                solicitarNuevoToken();
                return;
            } catch (HttpClientErrorException e) {
                // 4xx: credenciales o cliente mal configurados, reintentar no lo arregla
                throw e;
            } catch (RestClientException | IllegalStateException e) {
                if (intento >= MAX_INTENTOS) {
                    log.error("No se pudo obtener el token de SISTEMA tras {} intentos: {}", intento, e.getMessage());
                    throw e;
                }
                log.warn("Falló el pedido del token de SISTEMA (intento {} de {}): {}. Se reintenta en {} ms.",
                        intento, MAX_INTENTOS, e.getMessage(), pausa.toMillis());
                esperar(pausa);
                pausa = pausa.multipliedBy(2);
            }
        }
    }

    private void esperar(Duration pausa) {
        try {
            espera.esperar(pausa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera para reintentar el token de SISTEMA", e);
        }
    }

    private void solicitarNuevoToken() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("access_token")) {
            this.cachedToken = (String) response.get("access_token");
            Object expiresIn = response.get("expires_in");
            long segundos = (expiresIn instanceof Number n) ? n.longValue() : 300;
            this.expiresAt = Instant.now().plusSeconds(segundos);
        } else {
            throw new IllegalStateException("No se pudo obtener el token de servicio de Keycloak");
        }
    }

    @FunctionalInterface
    interface Espera {
        void esperar(Duration duracion) throws InterruptedException;
    }
}
