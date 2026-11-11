package com.obrasviales.ms_obras.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.dto.DatosOperacionResponse;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.service.OperacionService;
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

@WebMvcTest(OperacionController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class OperacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperacionService operacionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private DatosOperacionResponse datosDeEjemplo() {
        return new DatosOperacionResponse(
                1L,
                TipoFase.BASE,
                EstadoFase.PENDIENTE,
                true,
                10L,
                "Tramo Av. Libertad",
                new BigDecimal("1000.00"),
                -34.6,
                -58.4,
                List.of(new MaterialCalculado("Piedra triturada", new BigDecimal("250.00"), "m3")));
    }

    // SEG-01: sin token, el servicio responde 401
    @Test
    void datosOperacionSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/obras/fases/1/datos-operacion"))
                .andExpect(status().isUnauthorized());
    }

    // SEG-01: con un rol que no corresponde (OPERARIO), responde 403
    @Test
    void datosOperacionConRolInsuficienteDevuelve403() throws Exception {
        mockMvc.perform(get("/api/obras/fases/1/datos-operacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERARIO"))))
                .andExpect(status().isForbidden());
    }

    // SEG-01 e INT-01: con el rol SISTEMA (cuenta de servicio de Operaciones), responde 200
    @Test
    void datosOperacionConSistemaDevuelve200ConLosDatos() throws Exception {
        when(operacionService.obtenerDatosOperacion(1L)).thenReturn(datosDeEjemplo());

        mockMvc.perform(get("/api/obras/fases/1/datos-operacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faseId").value(1))
                .andExpect(jsonPath("$.tipoTrabajo").value("BASE"))
                .andExpect(jsonPath("$.faseAnteriorCompletada").value(true))
                .andExpect(jsonPath("$.tramoNombre").value("Tramo Av. Libertad"))
                .andExpect(jsonPath("$.latitud").value(-34.6))
                .andExpect(jsonPath("$.longitud").value(-58.4))
                .andExpect(jsonPath("$.materiales.length()").value(1))
                .andExpect(jsonPath("$.materiales[0].material").value("Piedra triturada"));
    }

    // Fase inexistente: el manejador de commons responde 404 con el detalle
    @Test
    void datosOperacionDeFaseInexistenteDevuelve404() throws Exception {
        when(operacionService.obtenerDatosOperacion(99L))
                .thenThrow(new RecursoNoEncontradoException("No existe la fase con id 99"));

        mockMvc.perform(get("/api/obras/fases/99/datos-operacion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la fase con id 99"));
    }
}