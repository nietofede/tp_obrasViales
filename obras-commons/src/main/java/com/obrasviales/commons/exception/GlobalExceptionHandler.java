package com.obrasviales.commons.exception; // Corregido: sin 'main.java'

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Los campos status, titulo, detalle y tipo son por el estandar RFC 7807
    private ProblemDetail crearProblemDetail(HttpStatus status, String titulo, String detalle, String tipo) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(titulo);
        problem.setType(URI.create(tipo));
        
        // Estos son los metadatos adicionales del estandar 
        problem.setProperty("timestamp", Instant.now());
        
        // pedimos el id, sirve para logs
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail handleNotFound(RecursoNoEncontradoException ex) {
        return crearProblemDetail(
            HttpStatus.NOT_FOUND, // status
            "Recurso no encontrado", // titulo
            ex.getMessage(), // detalle
            "urn:problem:not-found" // tipo
        );
    }

    
    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail handleReglaNegocio(ReglaNegocioException ex) {
        ProblemDetail problem = crearProblemDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, 
            "Incumple regla de negocio", 
            ex.getMessage(), 
            "urn:problem:business-rule"
        );
        problem.setProperty("codigoRegla", ex.getCodigoRegla());
        return problem;
    }

    @ExceptionHandler(ConflictoEstadoException.class)
    public ProblemDetail handleConflicto(ConflictoEstadoException ex) {
        return crearProblemDetail(
            HttpStatus.CONFLICT, 
            "Conflicto de estado", 
            ex.getMessage(), 
            "urn:problem:conflict"
        );
    }

    // capturar fallos de spring security de autorizacion
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return crearProblemDetail(
            HttpStatus.FORBIDDEN, 
            "Acceso denegado", 
            "No posee los permisos suficientes para esta acción", 
            "urn:problem:forbidden"
        );
    }

    // este es si se incumple en un dto alguna validacion en un campo, entonces
    // creamos hacemos un map con el campo y el error, y lo mandamos como property
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> 
            errores.put(err.getField(), err.getDefaultMessage())
        );
        
        ProblemDetail problem = crearProblemDetail(
            HttpStatus.BAD_REQUEST, 
            "Error de validación", 
            "Uno o más campos no cumplen con las validaciones", 
            "urn:problem:validation"
        );
        problem.setProperty("errores", errores);
        return problem;
    }

    // puse por las dudas una excepcion generica no controlada
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerico(Exception ex) {
        
        return crearProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            "Error interno del servidor", 
            "Ha ocurrido un error inesperado en el servidor. Por favor, intente más tarde.", 
            "urn:problem:internal-error"
        );
    }
}