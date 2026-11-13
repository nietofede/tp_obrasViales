package com.obrasviales.api_gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 403 de las reglas por ruta, metodo y rol del gateway, en el mismo formato que los 403 de los servicios.
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemJson.escribir(request, response, 403, "urn:problem:forbidden", "Acceso denegado",
                "No posee los permisos suficientes para esta acción");
    }
}
