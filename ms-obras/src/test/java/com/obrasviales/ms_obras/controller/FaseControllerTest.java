package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
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

@WebMvcTest(FaseController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class FaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaseService faseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Fase faseEnCurso() {
        Fase fase = new Fase();
        fase.setId(1L);
        fase.setTipo(TipoFase.BASE);
        fase.setEstado(EstadoFase.EN_CURSO);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 10));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 15));
        return fase;
    }

    // ---------- Iniciar fase ----------

    // ---------- Reprogramar fase ----------

    // SEG-01: CAPATAZ no puede reprogramar, responde 403
    @Test
    void reprogramarConRolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(patch("/api/obras/fases/1/programacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFechaInicio\":\"2026-12-13\"}"))
                .andExpect(status().isForbidden());
    }

    // SEG-01: PLANIFICADOR puede reprogramar, responde 200
    @Test
    void reprogramarConPlanificadorDevuelve200() throws Exception {
        when(faseService.reprogramarFase(eq(1L), eq(LocalDate.of(2026, 12, 13))))
                .thenReturn(faseEnCurso());

        mockMvc.perform(patch("/api/obras/fases/1/programacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFechaInicio\":\"2026-12-13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // Bean Validation: sin la fecha, responde 400 indicando qué campo falló
    @Test
    void reprogramarSinFechaDevuelve400() throws Exception {
        mockMvc.perform(patch("/api/obras/fases/1/programacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nuevaFechaInicio").exists());
    }
}