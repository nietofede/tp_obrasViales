package com.obrasviales.ms_operaciones.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("token_type") String tokenType
) {}