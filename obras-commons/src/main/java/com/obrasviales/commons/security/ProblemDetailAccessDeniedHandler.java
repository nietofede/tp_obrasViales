package com.obrasviales.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 403 que sale del filtro de seguridad (reglas por ruta). Los 403 de @PreAuthorize los resuelve
// GlobalExceptionHandler; este cubre el resto para que ambos tengan el mismo formato.
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemJson.escribir(request, response, 403, "urn:problem:forbidden", "Acceso denegado",
                "No posee los permisos suficientes para esta acción");
    }
}