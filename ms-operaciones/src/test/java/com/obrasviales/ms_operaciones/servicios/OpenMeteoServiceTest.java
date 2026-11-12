package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// RN-10: nunca se inventan datos de clima. Open-Meteo simulado, sin red.
class OpenMeteoServiceTest {

    private MockRestServiceServer openMeteo;
    private OpenMeteoService service;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        openMeteo = MockRestServiceServer.bindTo(builder).build();
        service = new OpenMeteoService(builder.build(), "http://open-meteo.test/v1/forecast");
    }

    private static String respuesta(String lluvia, String humedad, String temperatura) {
        return """
                {"current":{"time":"2026-11-12T22:45","temperature_2m":%s},
                 "hourly":{"time":["2026-11-12T21:00","2026-11-12T22:00","2026-11-12T23:00"],
                           "precipitation_probability":%s,
                           "soil_moisture_0_to_1cm":%s},
                 "otroCampo":"x"}
                """.formatted(temperatura, lluvia, humedad);
    }

    @Test
    void tomaLosDatosDeLaHoraActual() {
        openMeteo.expect(anything()).andExpect(request -> {
            String uri = request.getURI().toString();
            assertTrue(uri.startsWith("http://open-meteo.test/v1/forecast?"));
            assertTrue(uri.contains("latitude=-31.245"));
            assertTrue(uri.contains("hourly=precipitation_probability,soil_moisture_0_to_1cm"));
        }).andRespond(withSuccess(respuesta("[10,35,60]", "[0.31,0.32,0.33]", "17.5"), MediaType.APPLICATION_JSON));

        DatosClimaDTO datos = service.obtenerCondicionesClimaticas(-31.245, -64.456);

        assertEquals(17.5, datos.getTemperatura());
        assertEquals(35.0, datos.getProbabilidadLluvia());
        assertEquals(0.32, datos.getHumedadSuelo());
        openMeteo.verify();
    }

    @Test
    void sinProbabilidadDeLluviaFallaEnVezDeAsumirCero() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta("[null,null,null]", "[0.31,0.32,0.33]", "17.5"), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
    }

    @Test
    void sinHumedadDeSueloFallaEnVezDeAsumirCero() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta("[10,35,60]", "[]", "17.5"), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
    }

    @Test
    void sinTemperaturaFalla() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta("[10,35,60]", "[0.31,0.32,0.33]", "null"), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
    }

    @Test
    void siLaHoraActualNoEstaEnLosDatosHorariosFalla() {
        String sinHora = """
                {"current":{"time":"2026-11-12T05:10","temperature_2m":17.5},
                 "hourly":{"time":["2026-11-12T21:00"],"precipitation_probability":[10],"soil_moisture_0_to_1cm":[0.3]}}
                """;
        openMeteo.expect(anything()).andRespond(withSuccess(sinHora, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
    }

    @Test
    void siOpenMeteoFallaSePropagaElError() {
        openMeteo.expect(anything()).andRespond(withServerError());

        assertThrows(RuntimeException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
    }
}
