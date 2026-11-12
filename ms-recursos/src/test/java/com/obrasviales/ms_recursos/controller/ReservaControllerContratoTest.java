package com.obrasviales.ms_recursos.controller;

import com.obrasviales.commons.exception.GlobalExceptionHandler;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.commons.security.BaseResourceServerConfig;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.PlantaMezcla;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Contrato de "proponer y reservar": la respuesta trae los datos que Operaciones necesita (hora de salida del
// camión, planta, tiempo de mezcla), la entrada se valida (400) y los rechazos de regla salen con su código (422).
@WebMvcTest(ReservaController.class)
@Import({BaseResourceServerConfig.class, GlobalExceptionHandler.class})
class ReservaControllerContratoTest {

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

    private RequestPostProcessor sistema() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SISTEMA"));
    }

    private Reserva reservaSinMezcla() {
        Cuadrilla cuadrilla = new Cuadrilla();
        cuadrilla.setId(1L);

        Reserva reserva = new Reserva();
        reserva.setId(5L);
        reserva.setOrdenTrabajoId(101L);
        reserva.setCuadrilla(cuadrilla);
        reserva.setEstado("PROVISORIA");
        reserva.setFechaReserva(LocalDate.of(2026, 12, 4));
        reserva.setFechaVencimientoTtl(LocalDateTime.of(2026, 12, 4, 8, 30));
        reserva.setTiempoViajeCuadrillaMin(14);
        reserva.setHoraLlegadaCuadrilla(LocalTime.of(8, 14));
        reserva.setFuenteRuta("OSRM");
        return reserva;
    }

    // ---------- Respuesta ----------

    @Test
    void proponer_conMezcla_devuelveLosDatosDeMezcla() throws Exception {
        Reserva reserva = reservaSinMezcla();
        PlantaMezcla planta = new PlantaMezcla();
        planta.setId(3L);
        reserva.setPlanta(planta);
        reserva.setTiempoViajeMezclaMin(20);
        reserva.setHoraSalidaCamion(LocalTime.of(7, 39));
        when(reservaService.proponerYReservar(anyLong(), any(), any(), anyDouble(), anyDouble())).thenReturn(reserva);

        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantaId").value(3))
                .andExpect(jsonPath("$.tiempoViajeMezclaMin").value(20))
                .andExpect(jsonPath("$.horaSalidaCamion", startsWith("07:39")))
                .andExpect(jsonPath("$.horaLlegadaCuadrilla", startsWith("08:14")))
                .andExpect(jsonPath("$.fuenteRuta").value("OSRM"));
    }

    @Test
    void proponer_sinMezcla_devuelveLosDatosDeMezclaVacios() throws Exception {
        when(reservaService.proponerYReservar(anyLong(), any(), any(), anyDouble(), anyDouble()))
                .thenReturn(reservaSinMezcla());

        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantaId", nullValue()))
                .andExpect(jsonPath("$.tiempoViajeMezclaMin", nullValue()))
                .andExpect(jsonPath("$.horaSalidaCamion", nullValue()));
    }

    // ---------- Validación de la entrada ----------

    @Test
    void proponer_cuerpoVacio_devuelve400() throws Exception {
        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").exists());
    }

    @Test
    void proponer_latitudFueraDeRango_devuelve400() throws Exception {
        String body = "{\"ordenTrabajoId\":101,\"tipoTrabajo\":\"BASE\",\"fecha\":\"2026-12-04\","
                + "\"tramoLat\":95,\"tramoLon\":-64.18}";

        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tramoLat").exists());
    }

    @Test
    void proponer_tipoDeTrabajoEnBlanco_devuelve400() throws Exception {
        String body = "{\"ordenTrabajoId\":101,\"tipoTrabajo\":\"\",\"fecha\":\"2026-12-04\","
                + "\"tramoLat\":-31.42,\"tramoLon\":-64.18}";

        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipoTrabajo").exists());
    }

    // ---------- Rechazo por regla ----------

    @Test
    void proponer_reglaIncumplida_devuelve422ConElCodigoDeLaRegla() throws Exception {
        when(reservaService.proponerYReservar(anyLong(), any(), any(), anyDouble(), anyDouble()))
                .thenThrow(new ReglaNegocioException("Ninguna cuadrilla es elegible. Alfa: RN-07, viaje de 600 min", "RN-07"));

        mockMvc.perform(post(URL).with(sistema()).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigoRegla").value("RN-07"));
    }
}
