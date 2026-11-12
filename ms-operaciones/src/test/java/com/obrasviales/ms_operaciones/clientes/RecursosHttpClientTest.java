package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.dtos.ReservaDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Simula a ms-recursos con MockRestServiceServer: no hace falta Recursos, Keycloak ni Postgres.
class RecursosHttpClientTest {

    private static final String BASE = "http://recursos.test/api/recursos/internos/reservas";

    private MockRestServiceServer recursos;
    private RecursosHttpClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        recursos = MockRestServiceServer.bindTo(builder).build();
        cliente = new RecursosHttpClient(builder.build(), "http://recursos.test");
    }

    private String reservaJson(String estado) {
        return """
                {"id":7,"ordenTrabajoId":10,"estado":"%s","fechaReserva":"2026-11-13",
                 "fechaVencimientoTtl":"2026-11-12T20:30:00","cuadrillaId":1,"plantaId":1,
                 "tiempoViajeCuadrillaMin":35,"tiempoViajeMezclaMin":20,
                 "horaLlegadaCuadrilla":"08:35:00","horaSalidaCamion":"08:00:00","fuenteRuta":"OSRM",
                 "campoNuevo":"x"}
                """.formatted(estado);
    }

    private static String problema(String detalle, String regla) {
        return "{\"detail\":\"" + detalle + "\",\"status\":422,\"codigoRegla\":\"" + regla + "\"}";
    }

    @Test
    void reservaRecursosEnviandoLosDatosDeLaOrdenYDevuelveLaReserva() {
        recursos.expect(requestTo(BASE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ordenTrabajoId").value(10))
                .andExpect(jsonPath("$.tipoTrabajo").value("CARPETA_ASFALTICA"))
                .andExpect(jsonPath("$.fecha").value("2026-11-13"))
                .andExpect(jsonPath("$.tramoLat").value(-31.245))
                .andExpect(jsonPath("$.tramoLon").value(-64.456))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(reservaJson("PROVISORIA")));

        ReservaDTO reserva = cliente.proponerYReservar(10L, "CARPETA_ASFALTICA", LocalDate.of(2026, 11, 13), -31.245, -64.456);

        assertEquals(7L, reserva.id());
        assertEquals("PROVISORIA", reserva.estado());
        assertEquals(LocalTime.of(8, 0), reserva.horaSalidaCamion());
        recursos.verify();
    }

    @Test
    void unRechazoDeReglaSeReenviaConSuCodigo() {
        recursos.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problema("Ninguna cuadrilla es elegible.", "RN-07")));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> cliente.proponerYReservar(10L, "BASE", LocalDate.of(2026, 11, 13), -54.8, -68.3));

        assertEquals("RN-07", ex.getCodigoRegla());
        assertEquals("Ninguna cuadrilla es elegible.", ex.getMessage());
    }

    @Test
    void confirmaUnaReserva() {
        recursos.expect(requestTo(BASE + "/7/confirmacion"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(reservaJson("CONFIRMADA"), MediaType.APPLICATION_JSON));

        assertEquals("CONFIRMADA", cliente.confirmar(7L).estado());
        recursos.verify();
    }

    @Test
    void confirmarUnaReservaVencidaLanzaConflicto() {
        recursos.expect(requestTo(BASE + "/7/confirmacion"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"La reserva provisoria ya expiró por TTL\",\"status\":409}"));

        assertThrows(ConflictoEstadoException.class, () -> cliente.confirmar(7L));
    }

    @Test
    void confirmarUnaReservaInexistenteLanzaRecursoNoEncontrado() {
        recursos.expect(requestTo(BASE + "/99/confirmacion"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"Reserva no encontrada: 99\",\"status\":404}"));

        assertThrows(RecursoNoEncontradoException.class, () -> cliente.confirmar(99L));
    }

    @Test
    void liberaUnaReserva() {
        recursos.expect(requestTo(BASE + "/7/liberacion"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(reservaJson("LIBERADA"), MediaType.APPLICATION_JSON));

        assertEquals("LIBERADA", cliente.liberar(7L).estado());
        recursos.verify();
    }

    @Test
    void siRecursosFallaLanzaServicioNoDisponible() {
        recursos.expect(requestTo(BASE)).andRespond(withServerError());

        assertThrows(ServicioNoDisponibleException.class,
                () -> cliente.proponerYReservar(10L, "BASE", LocalDate.of(2026, 11, 13), -31.245, -64.456));
    }
}
