package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.dto.ObraResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
import com.obrasviales.ms_obras.service.ObraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ObraController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ObraControllerConsultasTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObraService obraService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ---------- Listar obras ----------

    @Test
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarConAuditorDevuelve200ConLasObras() throws Exception {
        when(obraService.listarObras()).thenReturn(List.of(
                new ObraResponse(1L, "Ruta 34", "Acceso norte"),
                new ObraResponse(2L, "Av. Libertad", null)));

        mockMvc.perform(get("/api/obras")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Ruta 34"));
    }

    // ---------- Ver una obra ----------

    @Test
    void obtenerConCapatazDevuelve200() throws Exception {
        when(obraService.obtenerObra(1L)).thenReturn(new ObraResponse(1L, "Ruta 34", "Acceso norte"));

        mockMvc.perform(get("/api/obras/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.descripcion").value("Acceso norte"));
    }

    @Test
    void obtenerObraInexistenteDevuelve404() throws Exception {
        when(obraService.obtenerObra(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(get("/api/obras/99")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la obra con id 99"));
    }

    // ---------- Listar los tramos de una obra ----------

    @Test
    void tramosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/5/tramos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tramosConPlanificadorDevuelve200ConLosTramos() throws Exception {
        when(obraService.listarTramos(5L)).thenReturn(List.of(
                new TramoResponse(20L, 5L, "Tramo norte", new BigDecimal("1000.00"), -34.6, -58.4)));

        mockMvc.perform(get("/api/obras/5/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].obraId").value(5))
                .andExpect(jsonPath("$[0].nombre").value("Tramo norte"));
    }

    @Test
    void tramosDeObraInexistenteDevuelve404() throws Exception {
        when(obraService.listarTramos(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(get("/api/obras/99/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isNotFound());
    }

    // ---------- 401 y 403 que completan los tres casos de cada GET ----------

    @Test
    void obtenerSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tramosConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/5/tramos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO"))))
                .andExpect(status().isForbidden());
    }
}
