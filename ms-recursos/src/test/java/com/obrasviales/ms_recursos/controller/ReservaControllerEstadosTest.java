package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Los errores de estado salen con el formato estandar de commons: 404 si la reserva no existe y 409 si el estado no lo permite.
@WebMvcTest(ReservaController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ReservaControllerEstadosTest {

    private static final String URL = "/api/recursos/internos/reservas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void confirmar_reservaInexistente_devuelve404() throws Exception {
        when(reservaService.confirmarReserva(9999L))
                .thenThrow(new RecursoNoEncontradoException("Reserva no encontrada: 9999"));

        mockMvc.perform(post(URL + "/9999/confirmar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmar_estadoQueNoLoPermite_devuelve409() throws Exception {
        when(reservaService.confirmarReserva(5L))
                .thenThrow(new ConflictoEstadoException("No se puede confirmar una reserva en estado LIBERADA"));

        mockMvc.perform(post(URL + "/5/confirmar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isConflict());
    }

    @Test
    void liberar_reservaInexistente_devuelve404() throws Exception {
        when(reservaService.liberarReserva(9999L))
                .thenThrow(new RecursoNoEncontradoException("Reserva no encontrada: 9999"));

        mockMvc.perform(post(URL + "/9999/liberar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isNotFound());
    }
}