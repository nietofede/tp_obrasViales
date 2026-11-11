package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.Obra;
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
}