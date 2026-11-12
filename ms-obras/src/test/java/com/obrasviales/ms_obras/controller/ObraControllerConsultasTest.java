package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.ObraDetalleResponse;
import com.obrasviales.ms_obras.dto.ObraResumenResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ObraController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ObraControllerConsultasTest {

    private static final String OBRA_EDITADA =
            "{\"nombre\":\"Nuevo nombre\",\"descripcion\":\"Nueva descripcion\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObraService obraService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority rol(String rol) {
        return new SimpleGrantedAuthority("ROLE_" + rol);
    }

    private FaseResponse faseEnCurso() {
        return new FaseResponse(30L, 20L, TipoFase.BASE, EstadoFase.EN_CURSO,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), null, 40);
    }

    // ---------- GET /api/obras ----------

    @Test
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarConAuditorDevuelve200ConEstadoYAvance() throws Exception {
        when(obraService.listarObras()).thenReturn(List.of(
                new ObraResumenResponse(1L, "Ruta 34", "Acceso norte", "EN_EJECUCION", 50),
                new ObraResumenResponse(2L, "Av. Libertad", null, "PLANIFICADA", 0)));

        mockMvc.perform(get("/api/obras").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Ruta 34"))
                .andExpect(jsonPath("$[0].estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$[0].avance").value(50));
    }

    // ---------- GET /api/obras/{obraId} ----------

    @Test
    void obtenerSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/5").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerConCapatazDevuelve200ConSusTramosYFases() throws Exception {
        TramoResponse tramo = new TramoResponse(20L, 5L, "Tramo norte",
                new BigDecimal("1000.00"), -34.6, -58.4);
        when(obraService.obtenerObra(5L)).thenReturn(new ObraDetalleResponse(
                5L, "Ruta 34", "Acceso norte", "EN_EJECUCION", 40,
                List.of(new ObraDetalleResponse.TramoConFases(tramo, List.of(faseEnCurso())))));

        mockMvc.perform(get("/api/obras/5").with(jwt().authorities(rol("CAPATAZ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.avance").value(40))
                .andExpect(jsonPath("$.tramos[0].tramo.nombre").value("Tramo norte"))
                .andExpect(jsonPath("$.tramos[0].fases[0].tipo").value("BASE"))
                .andExpect(jsonPath("$.tramos[0].fases[0].avance").value(40));
    }

    @Test
    void obtenerObraInexistenteDevuelve404() throws Exception {
        when(obraService.obtenerObra(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(get("/api/obras/99").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la obra con id 99"));
    }

    // ---------- PUT /api/obras/{obraId} ----------

    @Test
    void actualizarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(put("/api/obras/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBRA_EDITADA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarConCapatazDevuelve403() throws Exception {
        mockMvc.perform(put("/api/obras/7")
                        .with(jwt().authorities(rol("CAPATAZ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBRA_EDITADA))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarConPlanificadorDevuelve200ConLaObraEditada() throws Exception {
        Obra obra = new Obra();
        obra.setId(7L);
        obra.setNombre("Nuevo nombre");
        obra.setDescripcion("Nueva descripcion");
        when(obraService.actualizarObra(7L, "Nuevo nombre", "Nueva descripcion")).thenReturn(obra);

        mockMvc.perform(put("/api/obras/7")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBRA_EDITADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombre").value("Nuevo nombre"));
    }

    @Test
    void actualizarConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(put("/api/obras/7")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"descripcion\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void actualizarObraInexistenteDevuelve404() throws Exception {
        when(obraService.actualizarObra(eq(99L), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(put("/api/obras/99")
                        .with(jwt().authorities(rol("PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBRA_EDITADA))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/obras/{obraId}/tramos ----------

    @Test
    void tramosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/5/tramos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tramosConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/5/tramos").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tramosConPlanificadorDevuelve200ConLosTramos() throws Exception {
        when(obraService.listarTramos(5L)).thenReturn(List.of(
                new TramoResponse(20L, 5L, "Tramo norte", new BigDecimal("1000.00"), -34.6, -58.4)));

        mockMvc.perform(get("/api/obras/5/tramos").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].obraId").value(5))
                .andExpect(jsonPath("$[0].nombre").value("Tramo norte"));
    }

    @Test
    void tramosDeObraInexistenteDevuelve404() throws Exception {
        when(obraService.listarTramos(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(get("/api/obras/99/tramos").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/obras/{obraId}/cronograma ----------

    @Test
    void cronogramaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/5/cronograma"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cronogramaConOperarioDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/5/cronograma").with(jwt().authorities(rol("OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cronogramaConAuditorDevuelve200ConLasFases() throws Exception {
        when(obraService.cronogramaDeObra(5L)).thenReturn(List.of(faseEnCurso()));

        mockMvc.perform(get("/api/obras/5/cronograma").with(jwt().authorities(rol("AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("EN_CURSO"))
                .andExpect(jsonPath("$[0].avance").value(40));
    }

    @Test
    void cronogramaDeObraInexistenteDevuelve404() throws Exception {
        when(obraService.cronogramaDeObra(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la obra con id 99"));

        mockMvc.perform(get("/api/obras/99/cronograma").with(jwt().authorities(rol("PLANIFICADOR"))))
                .andExpect(status().isNotFound());
    }
}
