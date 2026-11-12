package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.service.FaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaseInternaController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class FaseInternaControllerTest {

    private static final String AVANCE_100 = "{\"porcentaje\":100}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaseService faseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority rol(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }

    private Fase fase(EstadoFase estado, int avance) {
        Tramo tramo = new Tramo();
        tramo.setId(10L);
        Fase fase = new Fase();
        fase.setId(1L);
        fase.setTramo(tramo);
        fase.setTipo(TipoFase.BASE);
        fase.setEstado(estado);
        fase.setAvancePorcentaje(avance);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 1));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 5));
        return fase;
    }

    // ---------- POST /api/obras/internos/fases/{id}/avances ----------

    @Test
    void avanceSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE_100))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avanceConPlanificadorDevuelve403() throws Exception {
        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE_100))
                .andExpect(status().isForbidden());
    }

    @Test
    void avanceDel100ConSistemaDevuelve201YLaFaseCompletada() throws Exception {
        when(faseService.registrarAvance(eq(1L), eq(100))).thenReturn(fase(EstadoFase.COMPLETADA, 100));

        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE_100))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.avance").value(100));
    }

    @Test
    void avanceConPorcentajeCeroDevuelve400() throws Exception {
        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentaje\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.porcentaje").exists());
    }

    @Test
    void avanceConPorcentajeMayorA100Devuelve400() throws Exception {
        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"porcentaje\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.porcentaje").exists());
    }

    @Test
    void avanceEnUnaFaseCompletadaDevuelve409() throws Exception {
        when(faseService.registrarAvance(eq(1L), eq(100)))
                .thenThrow(new ConflictoEstadoException("La fase ya esta COMPLETADA"));

        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE_100))
                .andExpect(status().isConflict());
    }

    @Test
    void avanceQueRetrocedeDevuelve422ConElCodigoAvance() throws Exception {
        when(faseService.registrarAvance(eq(1L), eq(100)))
                .thenThrow(new ReglaNegocioException("El avance no puede retroceder", "AVANCE"));

        mockMvc.perform(post("/api/obras/internos/fases/1/avances")
                        .with(jwt().authorities(rol("SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE_100))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigoRegla").value("AVANCE"));
    }

    // ---------- PATCH /api/obras/internos/fases/{id}/riesgo ----------

    @Test
    void riesgoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(patch("/api/obras/internos/fases/1/riesgo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void riesgoConCapatazDevuelve403() throws Exception {
        mockMvc.perform(patch("/api/obras/internos/fases/1/riesgo")
                        .with(jwt().authorities(rol("CAPATAZ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void riesgoConSistemaDevuelve200YLaFaseEnRiesgo() throws Exception {
        when(faseService.marcarEnRiesgo(1L)).thenReturn(fase(EstadoFase.EN_RIESGO, 0));

        mockMvc.perform(patch("/api/obras/internos/fases/1/riesgo")
                        .with(jwt().authorities(rol("SISTEMA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_RIESGO"));
    }

    @Test
    void riesgoEnUnaFaseCompletadaDevuelve409() throws Exception {
        when(faseService.marcarEnRiesgo(1L))
                .thenThrow(new ConflictoEstadoException("No se puede marcar en riesgo una fase COMPLETADA"));

        mockMvc.perform(patch("/api/obras/internos/fases/1/riesgo")
                        .with(jwt().authorities(rol("SISTEMA"))))
                .andExpect(status().isConflict());
    }

    @Test
    void riesgoEnUnaFaseInexistenteDevuelve404() throws Exception {
        when(faseService.marcarEnRiesgo(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la fase con id 99"));

        mockMvc.perform(patch("/api/obras/internos/fases/99/riesgo")
                        .with(jwt().authorities(rol("SISTEMA"))))
                .andExpect(status().isNotFound());
    }
}
