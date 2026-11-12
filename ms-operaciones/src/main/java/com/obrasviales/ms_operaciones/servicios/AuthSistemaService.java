package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class AuthSistemaService {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public AuthSistemaService(
            @Value("${obrasviales.sistema-client.token-url}") String tokenUrl,
            @Value("${obrasviales.sistema-client.client-id}") String clientId,
            @Value("${obrasviales.sistema-client.client-secret}") String clientSecret) {
        this.restClient = RestClient.builder().build();
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String obtenerTokenSistema() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        TokenResponse response = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("No se pudo obtener el token de autenticación para SISTEMA");
        }

        return response.accessToken();
    }
}