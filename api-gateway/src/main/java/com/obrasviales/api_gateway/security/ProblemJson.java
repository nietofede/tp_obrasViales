package com.obrasviales.api_gateway.security;

import com.obrasviales.api_gateway.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Instant;

// Mismo formato estandar (RFC 7807) que obras-commons para los 401 y 403 del filtro de seguridad.
// Se repite aca porque el gateway no depende de commons; si cambia uno, hay que cambiar el otro.
final class ProblemJson {

    private ProblemJson() {
    }

    static void escribir(HttpServletRequest request, HttpServletResponse response, int status, String type,
                         String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");

        StringBuilder json = new StringBuilder("{");
        json.append("\"type\":\"").append(escapar(type)).append("\",");
        json.append("\"title\":\"").append(escapar(title)).append("\",");
        json.append("\"status\":").append(status).append(',');
        json.append("\"detail\":\"").append(escapar(detail)).append("\",");
        json.append("\"instance\":\"").append(escapar(request.getRequestURI())).append("\",");
        json.append("\"timestamp\":\"").append(Instant.now()).append('"');
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            json.append(",\"correlationId\":\"").append(escapar(correlationId)).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }

    // Escapa lo minimo para que el texto sea valido dentro de un string JSON
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c < 0x20 ? ' ' : c);
            }
        }
        return sb.toString();
    }
}
