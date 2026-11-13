package com.obrasviales.ms_operaciones.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_operaciones.servicios.EjecucionService;
import com.obrasviales.ms_operaciones.servicios.FaseEnRiesgoService;
import com.obrasviales.ms_operaciones.servicios.HabilitacionDiariaService;
import com.obrasviales.ms_operaciones.servicios.OTServiceInterfaz;
import com.obrasviales.ms_operaciones.servicios.SuspensionService;
import com.obrasviales.ms_operaciones.servicios.UmbralClimaticoService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// los tres casos obligatorios por endpoint (docs/matriz-permisos.md, seccion 4): sin token 401, rol insuficiente 403
// y rol correcto 2xx. los servicios estan simulados: se prueba la seguridad y que un cuerpo valido se acepta.
@WebMvcTest({OTController.class, EjecucionController.class, HabilitacionDiariaController.class,
        SuspensionController.class, FaseEnRiesgoController.class, ConfiguracionController.class,
        UmbralClimaticoController.class})
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class SeguridadEndpointsTest {

    private static final String ORDENES = "/api/operaciones/ordenes";
    private static final String UMBRAL_JSON =
            "{\"humedadSueloMaxima\":0.4,\"probPrecipitacionMaxima\":20.0,\"temperaturaMinima\":5.0}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private OTServiceInterfaz otService;
    @MockitoBean private EjecucionService ejecucionService;
    @MockitoBean private HabilitacionDiariaService habilitacionDiariaService;
    @MockitoBean private SuspensionService suspensionService;
    @MockitoBean private FaseEnRiesgoService faseEnRiesgoService;
    @MockitoBean private UmbralClimaticoService umbralClimaticoService;
    @MockitoBean private JwtDecoder jwtDecoder;

    // rolIncorrecto es null cuando el endpoint solo exige estar autenticado (no hay 403 posible)
    record Caso(String metodo, String ruta, String cuerpo, String rolIncorrecto, String rolCorrecto, int estado) {
        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    static Stream<Caso> casos() {
        return Stream.of(
                new Caso("POST", ORDENES, "{\"faseId\":1,\"tramoId\":1,\"fechaProgramada\":\"2099-01-01\"}", "CAPATAZ", "PLANIFICADOR", 201),
                new Caso("GET", ORDENES + "/1", null, null, "AUDITOR", 200),
                new Caso("GET", ORDENES, null, null, "AUDITOR", 200),
                new Caso("POST", ORDENES + "/1/habilitacion", null, "CAPATAZ", "PLANIFICADOR", 200),
                new Caso("GET", ORDENES + "/1/evaluaciones-climaticas", null, null, "AUDITOR", 200),
                new Caso("PATCH", ORDENES + "/1/cancelacion", null, "CAPATAZ", "PLANIFICADOR", 200),
                new Caso("PATCH", ORDENES + "/1/reprogramacion", "{\"fecha\":\"2099-01-01\"}", "CAPATAZ", "PLANIFICADOR", 200),
                new Caso("PATCH", ORDENES + "/1/resolucion-climatica", "{\"decision\":\"HABILITAR\"}", "PLANIFICADOR", "CAPATAZ", 200),
                new Caso("PATCH", ORDENES + "/1/inicio", null, "PLANIFICADOR", "CAPATAZ", 200),
                new Caso("PATCH", ORDENES + "/1/rechazo", "{\"motivo\":\"Falta de personal\"}", "PLANIFICADOR", "CAPATAZ", 200),
                new Caso("POST", ORDENES + "/1/avances", "{\"porcentaje\":40}", "PLANIFICADOR", "CAPATAZ", 201),
                new Caso("PATCH", ORDENES + "/1/cierre", "{\"horasMaquina\":7.5,\"materiales\":[]}", "PLANIFICADOR", "CAPATAZ", 200),
                new Caso("POST", "/api/operaciones/habilitaciones-diarias", "{\"fecha\":\"2026-11-13\"}", "CAPATAZ", "PLANIFICADOR", 200),
                new Caso("GET", "/api/operaciones/suspensiones", null, "CAPATAZ", "AUDITOR", 200),
                new Caso("GET", "/api/operaciones/fases-en-riesgo", null, "CAPATAZ", "AUDITOR", 200),
                new Caso("POST", "/api/operaciones/fases-en-riesgo/1/decisiones", "{\"decision\":\"CANCELAR\"}", "AUDITOR", "PLANIFICADOR", 200),
                new Caso("PUT", "/api/operaciones/configuracion/limite-suspensiones", "{\"limite\":3}", "PLANIFICADOR", "ADMIN", 200),
                new Caso("GET", "/api/operaciones/umbrales-climaticos", null, "OPERARIO", "AUDITOR", 200),
                new Caso("PUT", "/api/operaciones/umbrales-climaticos/BASE", UMBRAL_JSON, "PLANIFICADOR", "ADMIN", 200));
    }

    private static SimpleGrantedAuthority rol(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }

    private MockHttpServletRequestBuilder peticion(Caso caso) {
        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.request(HttpMethod.valueOf(caso.metodo()), caso.ruta());
        if (caso.cuerpo() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(caso.cuerpo());
        }
        return request;
    }

    // el cambio de estado libre se quito: dejaba pasar una orden a EN_EJECUCION sin reserva ni clima.
    // cada transicion tiene su endpoint (habilitacion, inicio, rechazo, cierre, cancelacion, reprogramacion).
    // hoy una ruta inexistente da 500 (lo corrige el manejo de errores de commons); aca solo importa que no se acepte.
    @Test
    void elCambioDeEstadoLibreYaNoExiste() throws Exception {
        for (String rolUsuario : new String[] {"PLANIFICADOR", "CAPATAZ"}) {
            mockMvc.perform(MockMvcRequestBuilders.patch(ORDENES + "/1/estado?nuevoEstado=EN_EJECUCION")
                            .with(jwt().authorities(rol(rolUsuario))))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void sinTokenDevuelve401(Caso caso) throws Exception {
        mockMvc.perform(peticion(caso)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void conRolInsuficienteDevuelve403(Caso caso) throws Exception {
        Assumptions.assumeTrue(caso.rolIncorrecto() != null, "el endpoint solo exige estar autenticado");

        mockMvc.perform(peticion(caso).with(jwt().authorities(rol(caso.rolIncorrecto()))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void conElRolCorrectoDevuelve2xx(Caso caso) throws Exception {
        mockMvc.perform(peticion(caso).with(jwt().authorities(rol(caso.rolCorrecto()))))
                .andExpect(status().is(caso.estado()));
    }
}
