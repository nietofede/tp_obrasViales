package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.service.CronogramaService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TramoController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class TramoControllerTest {

    private static final String FASE_VALIDA =
            "{\"tipo\":\"BASE\",\"fechaInicioPlanificada\":\"2026-12-06\","
                    + "\"fechaFinPlanificada\":\"2026-12-09\",\"faseAnteriorId\":1}";

    private static final String FASE_SIN_TIPO =
            "{\"fechaInicioPlanificada\":\"2026-12-06\",\"fechaFinPlanificada\":\"2026-12-09\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaseService faseService;

    @MockitoBean
    private CronogramaService cronogramaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Fase faseCreada() {
        Tramo tramo = new Tramo();
        tramo.setId(10L);
        Fase anterior = new Fase();
        anterior.setId(1L);
        Fase fase = new Fase();
        fase.setId(30L);
        fase.setTramo(tramo);
        fase.setFaseAnterior(anterior);
        fase.setTipo(TipoFase.BASE);
        fase.setEstado(EstadoFase.PENDIENTE);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 6));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 9));
        return fase;
    }

    private FaseResponse respuesta(Long id, TipoFase tipo) {
        return new FaseResponse(id, 10L, tipo, EstadoFase.PENDIENTE,
                LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9), null);
    }

    // ---------- Crear fase ----------

    @Test
    void crearFaseSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_VALIDA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearFaseConRolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_VALIDA))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearFaseConPlanificadorDevuelve201ConLaFase() throws Exception {
        when(faseService.crearFase(any(), any(), any(), any(), any())).thenReturn(faseCreada());

        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.tramoId").value(10))
                .andExpect(jsonPath("$.faseAnteriorId").value(1))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void crearFaseSinTipoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_SIN_TIPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipo").exists());
    }

    @Test
    void crearFaseEnTramoInexistenteDevuelve404() throws Exception {
        when(faseService.crearFase(any(), any(), any(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe el tramo con id 10"));

        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_VALIDA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el tramo con id 10"));
    }

    @Test
    void crearFaseConAnteriorDeOtroTramoDevuelve422ConCodigoDeRegla() throws Exception {
        when(faseService.crearFase(any(), any(), any(), any(), any()))
                .thenThrow(new ReglaNegocioException(
                        "La fase anterior debe pertenecer al mismo tramo", "FASE-TRAMO"));

        mockMvc.perform(post("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FASE_VALIDA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigoRegla").value("FASE-TRAMO"));
    }

    // ---------- Consultar cronograma ----------

    @Test
    void cronogramaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/tramos/10/fases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cronogramaConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cronogramaConAuditorDevuelve200ConLasFases() throws Exception {
        when(cronogramaService.cronogramaDelTramo(10L))
                .thenReturn(List.of(respuesta(1L, TipoFase.BASE), respuesta(2L, TipoFase.SUBBASE)));

        mockMvc.perform(get("/api/obras/tramos/10/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("BASE"))
                .andExpect(jsonPath("$[1].tipo").value("SUBBASE"));
    }

    @Test
    void cronogramaDeTramoInexistenteDevuelve404() throws Exception {
        when(cronogramaService.cronogramaDelTramo(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe el tramo con id 99"));

        mockMvc.perform(get("/api/obras/tramos/99/fases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el tramo con id 99"));
    }
}