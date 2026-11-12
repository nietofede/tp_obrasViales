package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Reserva;
import com.obrasviales.ms_recursos.service.ReservaService;
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
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEG-01: los endpoints internos de reservas son solo para la cuenta de servicio SISTEMA.
// Tres casos por endpoint: sin token (401), rol insuficiente (403) y rol correcto (2xx).
@WebMvcTest(ReservaController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ReservaControllerSeguridadTest {

    private static final String URL = "/api/recursos/internos/reservas";

    private static final String BODY_VALIDO =
            "{\"ordenTrabajoId\":101,\"tipoTrabajo\":\"CARPETA_ASFALTICA\",\"fecha\":\"2026-12-04\","
                    + "\"tramoLat\":-31.42,\"tramoLon\":-64.18}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Reserva reserva(String estado) {
        Cuadrilla cuadrilla = new Cuadrilla();
        cuadrilla.setId(1L);

        Reserva reserva = new Reserva();
        reserva.setId(5L);
        reserva.setOrdenTrabajoId(101L);
        reserva.setCuadrilla(cuadrilla);
        reserva.setEstado(estado);
        reserva.setFechaReserva(LocalDate.of(2026, 12, 4));
        reserva.setFechaVencimientoTtl(LocalDateTime.of(2026, 12, 4, 8, 30));
        reserva.setTiempoViajeCuadrillaMin(14);
        reserva.setHoraLlegadaCuadrilla(LocalTime.of(8, 14));
        reserva.setFuenteRuta("OSRM");
        return reserva;
    }

    // ---------- Proponer y reservar ----------

    @Test
    void proponer_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proponer_conPlanificador_devuelve403() throws Exception {
        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void proponer_conSistema_devuelve201() throws Exception {
        when(reservaService.proponerYReservar(anyLong(), any(), any(), anyDouble(), anyDouble()))
                .thenReturn(reserva("PROVISORIA"));

        mockMvc.perform(post(URL)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated());
    }

    // ---------- Confirmar ----------

    @Test
    void confirmar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(URL + "/5/confirmar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmar_conPlanificador_devuelve403() throws Exception {
        mockMvc.perform(post(URL + "/5/confirmar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmar_conSistema_devuelve200() throws Exception {
        when(reservaService.confirmarReserva(5L)).thenReturn(reserva("CONFIRMADA"));

        mockMvc.perform(post(URL + "/5/confirmar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isOk());
    }

    // ---------- Liberar ----------

    @Test
    void liberar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(URL + "/5/liberar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void liberar_conPlanificador_devuelve403() throws Exception {
        mockMvc.perform(post(URL + "/5/liberar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void liberar_conSistema_devuelve200() throws Exception {
        when(reservaService.liberarReserva(5L)).thenReturn(reserva("LIBERADA"));

        mockMvc.perform(post(URL + "/5/liberar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"))))
                .andExpect(status().isOk());
    }
}