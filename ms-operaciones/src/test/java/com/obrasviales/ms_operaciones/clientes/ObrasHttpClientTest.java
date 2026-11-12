package com.obrasviales.ms_operaciones.clientes;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Simula a ms-obras con MockRestServiceServer: no hace falta Obras, Keycloak ni Postgres.
class ObrasHttpClientTest {

    private static final String URL_CONTEXTO = "http://obras.test/api/obras/internos/fases/2/contexto-ejecucion";

    private MockRestServiceServer obras;
    private ObrasHttpClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        obras = MockRestServiceServer.bindTo(builder).build();
        cliente = new ObrasHttpClient(builder.build(), "http://obras.test");
    }

    @Test
    void devuelveElContextoDeLaFase() {
        obras.expect(requestTo(URL_CONTEXTO))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"faseId":2,"tipoTrabajo":"BASE","estadoFase":"PENDIENTE","faseAnteriorCompletada":true,
                         "tramoId":1,"tramoNombre":"Tramo 1","superficieM2":5000.00,
                         "latitud":-31.245,"longitud":-64.456,
                         "materiales":[{"material":"Grava","cantidad":1500.00,"unidad":"m3"}],
                         "campoNuevoQueNoConocemos":"x"}
                        """, MediaType.APPLICATION_JSON));

        ContextoFaseDTO contexto = cliente.obtenerContextoFase(2L);

        assertEquals("BASE", contexto.tipoTrabajo());
        assertTrue(contexto.faseAnteriorCompletada());
        assertEquals(-31.245, contexto.latitud());
        assertEquals(-64.456, contexto.longitud());
        assertEquals(1, contexto.materiales().size());
        obras.verify();
    }

    @Test
    void siLaFaseNoExisteEnObrasLanzaRecursoNoEncontrado() {
        obras.expect(requestTo(URL_CONTEXTO)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(RecursoNoEncontradoException.class, () -> cliente.obtenerContextoFase(2L));
    }

    @Test
    void siObrasFallaLanzaServicioNoDisponible() {
        obras.expect(requestTo(URL_CONTEXTO)).andRespond(withServerError());

        assertThrows(ServicioNoDisponibleException.class, () -> cliente.obtenerContextoFase(2L));
    }
}
