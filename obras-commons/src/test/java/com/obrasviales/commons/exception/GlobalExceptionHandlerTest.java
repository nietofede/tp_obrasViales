package com.obrasviales.commons.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TEC-01: todos los errores salen en el mismo formato (RFC 7807). Se usa un controlador de mentira que lanza cada
// excepcion y se comprueba el codigo HTTP, el tipo, los campos comunes y el registro del error inesperado.
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger logDelHandler;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FalsoController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // captura lo que el handler escribe en el log
        logDelHandler = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logDelHandler.addAppender(appender);
    }

    @AfterEach
    void limpiar() {
        logDelHandler.detachAppender(appender);
        MDC.clear();
    }

    // ---------- Excepciones de dominio ----------

    @Test
    void recursoNoEncontrado_devuelve404() throws Exception {
        mockMvc.perform(get("/falso/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:problem:not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No existe la cosa 7"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void reglaDeNegocio_devuelve422ConElCodigoDeLaRegla() throws Exception {
        mockMvc.perform(get("/falso/regla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:problem:business-rule"))
                .andExpect(jsonPath("$.codigoRegla").value("RN-99"))
                .andExpect(jsonPath("$.detail").value("No se cumple la regla"));
    }

    @Test
    void conflictoDeEstado_devuelve409() throws Exception {
        mockMvc.perform(get("/falso/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:conflict"));
    }

    @Test
    void accesoDenegado_devuelve403() throws Exception {
        mockMvc.perform(get("/falso/denegado"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem:forbidden"));
    }

    // ---------- Excepciones de Spring MVC (antes daban 500) ----------

    @Test
    void validacionDeCampos_devuelve400ConElDetallePorCampo() throws Exception {
        mockMvc.perform(post("/falso/validacion").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation"))
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"));
    }

    @Test
    void jsonIlegible_devuelve400() throws Exception {
        mockMvc.perform(post("/falso/validacion").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:unreadable-body"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void valorDeEnumInexistente_devuelve400() throws Exception {
        mockMvc.perform(post("/falso/color").contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"VIOLETA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:unreadable-body"));
    }

    @Test
    void metodoNoPermitido_devuelve405() throws Exception {
        mockMvc.perform(delete("/falso/no-encontrado"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:problem:method-not-allowed"))
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    @Test
    void rutaInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/falso/ruta/que/no/existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:not-found"));
    }

    @Test
    void parametroConTipoInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/falso/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:bad-request"));
    }

    // ---------- Correlation-id ----------

    @Test
    void sumaElCorrelationIdCuandoEstaEnElMdc() throws Exception {
        MDC.put("correlationId", "id-de-prueba");

        mockMvc.perform(get("/falso/no-encontrado"))
                .andExpect(jsonPath("$.correlationId").value("id-de-prueba"));
        mockMvc.perform(delete("/falso/no-encontrado"))
                .andExpect(jsonPath("$.correlationId").value("id-de-prueba"));
    }

    @Test
    void sinCorrelationIdNoInventaElCampo() throws Exception {
        mockMvc.perform(get("/falso/no-encontrado"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(hasKey("correlationId"))));
    }

    // ---------- Error inesperado ----------

    @Test
    void errorInesperado_devuelve500SinFiltrarElDetalleYLoRegistraEnElLog() throws Exception {
        mockMvc.perform(get("/falso/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:problem:internal-error"))
                // el cliente no debe ver el mensaje interno de la excepcion
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto interno"))));

        assertThat(appender.list)
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(evento.getFormattedMessage()).contains("Error no controlado");
                    assertThat(evento.getThrowableProxy().getMessage()).contains("secreto interno");
                });
    }

    // ---------- Controlador de mentira ----------

    enum Color { ROJO, VERDE }

    record Entrada(@NotBlank(message = "El nombre es obligatorio") String nombre) { }

    record EntradaColor(Color color) { }

    @RestController
    @RequestMapping("/falso")
    static class FalsoController {

        @GetMapping("/no-encontrado")
        String noEncontrado() {
            throw new RecursoNoEncontradoException("No existe la cosa 7");
        }

        @GetMapping("/regla")
        String regla() {
            throw new ReglaNegocioException("No se cumple la regla", "RN-99");
        }

        @GetMapping("/conflicto")
        String conflicto() {
            throw new ConflictoEstadoException("Estado incompatible");
        }

        @GetMapping("/denegado")
        String denegado() {
            throw new AccessDeniedException("sin permiso");
        }

        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("secreto interno");
        }

        @GetMapping("/numero/{n}")
        String numero(@PathVariable Long n) {
            return "ok " + n;
        }

        @PostMapping("/validacion")
        String validacion(@Valid @RequestBody Entrada entrada) {
            return entrada.nombre();
        }

        @PostMapping("/color")
        String color(@RequestBody EntradaColor entrada) {
            return String.valueOf(entrada.color());
        }
    }
}
