package com.obrasviales.commons.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;


//  Filtro HTTP que intercepta cada request entrante para asignar correlation id
// 7Hereda de OncePerRequestFilter para garantizar que solo se ejecute una vez por petición HTTP.

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Se ejecuta con máxima prioridad antes que el resto de los filtros de Spring
public class CorrelationIdFilter extends OncePerRequestFilter {

    // le ponemos un nombre estandarp ara pasar el ID entre microservicios
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    // y una clave para los logs
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // primero leemos el header de la petición entrante
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        // si no hay le generamos uuid
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        // ponemos el id en el mdc para los logs
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        
        // hacemo que devuelva el header tambien en la respuesta 
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            // seguimos con los otros filtros
            filterChain.doFilter(request, response);
        } finally {
            // cuando no hay mas filtros limpiamos el mdc 
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}