package com.obrasviales.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

// 401: falta el token, esta vencido o no es valido. Antes salia con el cuerpo vacio.
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // El de Spring pone el status 401 y el header WWW-Authenticate (lo pide el estandar de Bearer tokens)
    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearer.commence(request, response, authException);
        ProblemJson.escribir(request, response, 401, "urn:problem:unauthorized", "No autenticado",
                "El token de acceso falta, venció o no es válido");
    }
}