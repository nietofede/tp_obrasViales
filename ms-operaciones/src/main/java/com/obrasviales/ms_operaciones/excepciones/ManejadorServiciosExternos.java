package com.obrasviales.ms_operaciones.excepciones;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

// se le da maxima prioridad, para que vaya antes que el handler generico 
// de commons, sino me paso que no se usaba
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorServiciosExternos {

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ProblemDetail handleServicioNoDisponible(ServicioNoDisponibleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Servicio no disponible");
        problem.setType(URI.create("urn:problem:service-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
