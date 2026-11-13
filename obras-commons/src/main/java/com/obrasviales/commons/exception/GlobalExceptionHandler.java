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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler{

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

    // Validacion de un DTO (Bean Validation): ahora es un override, porque la clase padre ya declara un
    // @ExceptionHandler para esta excepcion y dos serian ambiguos.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
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
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // JSON mal formado o con un valor que no entra en el tipo (por ejemplo un enum inexistente): 400
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = crearProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Solicitud ilegible",
            "El cuerpo de la solicitud no es un JSON válido o tiene un valor no permitido",
            "urn:problem:unreadable-body"
        );
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Todas las excepciones de Spring MVC (ruta inexistente, metodo no permitido, parametro faltante, etc.)
    // pasan por aca: se les suma el mismo formato (tipo, titulo, timestamp y correlationId).
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problem) {
            completarFormato(problem, statusCode);
        }
        return respuesta;
    }

    private void completarFormato(ProblemDetail problem, HttpStatusCode status) {
        boolean sinTipo = problem.getType() == null || "about:blank".equals(problem.getType().toString());
        if (sinTipo) {
            switch (status.value()) {
                case 404 -> { problem.setType(URI.create("urn:problem:not-found")); problem.setTitle("Recurso no encontrado"); }
                case 405 -> { problem.setType(URI.create("urn:problem:method-not-allowed")); problem.setTitle("Método no permitido"); }
                default -> problem.setType(URI.create("urn:problem:bad-request"));
            }
        }
        if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now());
        }
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }

    // puse por las dudas una excepcion generica no controlada
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerico(Exception ex) {
            log.error("Error no controlado", ex);
        return crearProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            "Error interno del servidor", 
            "Ha ocurrido un error inesperado en el servidor. Por favor, intente más tarde.", 
            "urn:problem:internal-error"
        );
    }
}