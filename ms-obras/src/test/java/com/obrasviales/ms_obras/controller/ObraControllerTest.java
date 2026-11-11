package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.service.ObraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ObraController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ObraControllerTest {

    private static final String BODY_VALIDO =
            "{\"nombre\":\"Repavimentacion Av. Libertad\",\"descripcion\":\"Tramo norte\"}";

    private static final String TRAMO_VALIDO =
            "{\"nombre\":\"Tramo norte\",\"superficieM2\":1000.00,\"latitud\":-34.6,\"longitud\":-58.4}";

    private static final String TRAMO_LATITUD_FUERA_DE_RANGO =
            "{\"nombre\":\"Tramo norte\",\"superficieM2\":1000.00,\"latitud\":91,\"longitud\":-58.4}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObraService obraService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Obra obraCreada() {
        Obra obra = new Obra();
        obra.setId(7L);
        obra.setNombre("Repavimentacion Av. Libertad");
        obra.setDescripcion("Tramo norte");
        return obra;
    }

    private Tramo tramoCreado() {
        Obra obra = new Obra();
        obra.setId(5L);
        Tramo tramo = new Tramo();
        tramo.setId(20L);
        tramo.setObra(obra);
        tramo.setNombre("Tramo norte");
        tramo.setSuperficieM2(new BigDecimal("1000.00"));
        tramo.setLatitud(-34.6);
        tramo.setLongitud(-58.4);
        return tramo;
    }

    // ---------- Crear obra ----------

    // SEG-01: sin token, el servicio responde 401
    @Test
    void crearSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/obras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    // SEG-01: con un rol que no corresponde (CAPATAZ), responde 403
    @Test
    void crearConRolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/obras")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    // SEG-01: con el rol PLANIFICADOR y datos válidos, responde 201 con la obra creada
    @Test
    void crearConPlanificadorDevuelve201ConLaObra() throws Exception {
        when(obraService.crearObra("Repavimentacion Av. Libertad", "Tramo norte"))
                .thenReturn(obraCreada());

        mockMvc.perform(post("/api/obras")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombre").value("Repavimentacion Av. Libertad"))
                .andExpect(jsonPath("$.descripcion").value("Tramo norte"));
    }

    // Bean Validation: con el nombre vacío, responde 400 indicando qué campo falló
    @Test
    void crearConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/obras")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"descripcion\":\"Tramo norte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    // ---------- Crear tramo ----------

    // SEG-01: sin token, el servicio responde 401
    @Test
    void crearTramoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/obras/5/tramos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    // SEG-01: con un rol que no corresponde (CAPATAZ), responde 403
    @Test
    void crearTramoConRolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/obras/5/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_VALIDO))
                .andExpect(status().isForbidden());
    }

    // SEG-01: con PLANIFICADOR y datos válidos, responde 201 con el tramo creado
    @Test
    void crearTramoConPlanificadorDevuelve201ConElTramo() throws Exception {
        when(obraService.crearTramo(eq(5L), eq("Tramo norte"), any(BigDecimal.class),
                eq(-34.6), eq(-58.4)))
                .thenReturn(tramoCreado());

        mockMvc.perform(post("/api/obras/5/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.obraId").value(5))
                .andExpect(jsonPath("$.nombre").value("Tramo norte"))
                .andExpect(jsonPath("$.latitud").value(-34.6))
                .andExpect(jsonPath("$.longitud").value(-58.4));
    }

    // Bean Validation: una latitud fuera de rango (91) responde 400 indicando el campo
    @Test
    void crearTramoConLatitudFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/obras/5/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_LATITUD_FUERA_DE_RANGO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.latitud").exists());
    }

    // Obra inexistente: el manejador de commons responde 404 con el detalle
    @Test
    void crearTramoEnObraInexistenteDevuelve404() throws Exception {
        when(obraService.crearTramo(eq(99L), any(), any(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(post("/api/obras/99/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la obra con id 99"));
    }
}