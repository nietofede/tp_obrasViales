package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaseController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class FaseControllerLecturaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaseService faseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority rol(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }

    private Fase fase() {
        Tramo tramo = new Tramo();
        tramo.setId(10L);
        Fase anterior = new Fase();
        anterior.setId(1L);
        Fase fase = new Fase();
        fase.setId(2L);
        fase.setTramo(tramo);
        fase.setFaseAnterior(anterior);
        fase.setTipo(TipoFase.SUBBASE);
        fase.setEstado(EstadoFase.EN_CURSO);
        fase.setAvancePorcentaje(40);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 6));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 9));
        return fase;
    }

    // ---------- GET /api/obras/fases/{id} ----------

    @Test
    void obtenerSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/fases/2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/fases/2").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerConAuditorDevuelve200ConEstadoFechasYAvance() throws Exception {
        when(faseService.obtenerFase(2L)).thenReturn(fase());

        mockMvc.perform(get("/api/obras/fases/2").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.tramoId").value(10))
                .andExpect(jsonPath("$.faseAnteriorId").value(1))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.avance").value(40))
                .andExpect(jsonPath("$.fechaInicioPlanificada").value("2026-12-06"));
    }

    @Test
    void obtenerFaseInexistenteDevuelve404() throws Exception {
        when(faseService.obtenerFase(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la fase con id 99"));

        mockMvc.perform(get("/api/obras/fases/99").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la fase con id 99"));
    }

    // ---------- GET /api/obras/fases/{id}/materiales ----------

    @Test
    void materialesSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/fases/2/materiales"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void materialesConAuditorDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/fases/2/materiales").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void materialesConCapatazDevuelve200ConLaListaDeMateriales() throws Exception {
        when(faseService.materialesDeFase(2L)).thenReturn(List.of(
                new MaterialCalculado("Piedra triturada", new BigDecimal("250.00"), "m3")));

        mockMvc.perform(get("/api/obras/fases/2/materiales").with(jwt().authorities(rol("CAPATAZ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].material").value("Piedra triturada"))
                .andExpect(jsonPath("$[0].unidad").value("m3"));
    }

    @Test
    void materialesDeFaseInexistenteDevuelve404() throws Exception {
        when(faseService.materialesDeFase(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la fase con id 99"));

        mockMvc.perform(get("/api/obras/fases/99/materiales").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isNotFound());
    }

    // ---------- PATCH /api/obras/fases/{id}/programacion ----------

    @Test
    void programacionSinTokenDevuelve401() throws Exception {
        mockMvc.perform(patch("/api/obras/fases/2/programacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFechaInicio\":\"2026-12-13\"}"))
                .andExpect(status().isUnauthorized());
    }
}
