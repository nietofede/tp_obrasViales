package com.obrasviales.commons.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Service
public class SistemaTokenService {

    private final SistemaClientProperties properties;
    private final RestClient restClient;

    private String cachedToken;
    private Instant expiresAt = Instant.MIN;

    public SistemaTokenService(SistemaClientProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public synchronized String obtenerToken() {
        // Renueva el token si no existe o si faltan 30 segundos para vencer
        if (cachedToken == null || Instant.now().plusSeconds(30).isAfter(expiresAt)) {
            solicitarNuevoToken();
        }
        return cachedToken;
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
            Integer expiresInSeconds = (Integer) response.get("expires_in");
            this.expiresAt = Instant.now().plusSeconds(expiresInSeconds != null ? expiresInSeconds : 300);
        } else {
            throw new IllegalStateException("No se pudo obtener el token de servicio de Keycloak");
        }
    }
}