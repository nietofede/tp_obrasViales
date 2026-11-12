package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.service.CronogramaService;
import com.obrasviales.ms_obras.service.FaseService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TramoController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class TramoControllerEdicionTest {

    private static final String TRAMO_EDITADO =
            "{\"nombre\":\"Tramo editado\",\"superficieM2\":1500.00,\"latitud\":-34.7,\"longitud\":-58.5}";

    private static final String TRAMO_LATITUD_FUERA_DE_RANGO =
            "{\"nombre\":\"Tramo editado\",\"superficieM2\":1500.00,\"latitud\":91,\"longitud\":-58.5}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaseService faseService;

    @MockitoBean
    private CronogramaService cronogramaService;

    @MockitoBean
    private ObraService obraService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority rol(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }

    private Tramo tramo() {
        Obra obra = new Obra();
        obra.setId(5L);
        Tramo tramo = new Tramo();
        tramo.setId(20L);
        tramo.setObra(obra);
        tramo.setNombre("Tramo editado");
        tramo.setSuperficieM2(new BigDecimal("1500.00"));
        tramo.setLatitud(-34.7);
        tramo.setLongitud(-58.5);
        return tramo;
    }

    // ---------- GET /api/obras/tramos/{tramoId} ----------

    @Test
    void obtenerSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/tramos/20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/tramos/20").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerConAuditorDevuelve200ConElTramo() throws Exception {
        when(obraService.obtenerTramo(20L)).thenReturn(tramo());

        mockMvc.perform(get("/api/obras/tramos/20").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.obraId").value(5))
                .andExpect(jsonPath("$.latitud").value(-34.7));
    }

    @Test
    void obtenerTramoInexistenteDevuelve404() throws Exception {
        when(obraService.obtenerTramo(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe el tramo con id 99"));

        mockMvc.perform(get("/api/obras/tramos/99").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el tramo con id 99"));
    }

    // ---------- PUT /api/obras/tramos/{tramoId} ----------

    @Test
    void actualizarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(put("/api/obras/tramos/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_EDITADO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarConCapatazDevuelve403() throws Exception {
        mockMvc.perform(put("/api/obras/tramos/20")
                        .with(jwt().authorities(rol("CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_EDITADO))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarConPlanificadorDevuelve200ConElTramoEditado() throws Exception {
        when(obraService.actualizarTramo(eq(20L), eq("Tramo editado"), any(BigDecimal.class),
                eq(-34.7), eq(-58.5)))
                .thenReturn(tramo());

        mockMvc.perform(put("/api/obras/tramos/20")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_EDITADO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tramo editado"))
                .andExpect(jsonPath("$.longitud").value(-58.5));
    }

    @Test
    void actualizarConLatitudFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(put("/api/obras/tramos/20")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_LATITUD_FUERA_DE_RANGO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.latitud").exists());
    }

    @Test
    void actualizarTramoInexistenteDevuelve404() throws Exception {
        when(obraService.actualizarTramo(eq(99L), any(), any(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe el tramo con id 99"));

        mockMvc.perform(put("/api/obras/tramos/99")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRAMO_EDITADO))
                .andExpect(status().isNotFound());
    }
}
