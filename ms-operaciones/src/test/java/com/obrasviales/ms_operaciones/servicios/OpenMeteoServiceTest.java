package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// RN-09 y RN-10: se evalúa la ventana de ejecución de la fecha de la orden (por defecto de 08:00 a 13:00, 6 horas)
// tomando el peor valor de cada variable, y nunca se inventan datos. Open-Meteo simulado, sin red.
class OpenMeteoServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);

    private MockRestServiceServer openMeteo;
    private OpenMeteoService service;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        openMeteo = MockRestServiceServer.bindTo(builder).build();
        service = new OpenMeteoService(builder.build(), "http://open-meteo.test/v1/forecast");
    }

    // 24 horas del día con un valor por hora; las listas se arman con el valor de cada hora (índice = hora).
    private static String respuesta(List<String> temperatura, List<String> lluvia, List<String> humedad) {
        String horas = IntStream.range(0, 24)
                .mapToObj(h -> "\"%sT%02d:00\"".formatted(FECHA, h))
                .collect(Collectors.joining(","));
        return """
                {"hourly":{"time":[%s],
                           "temperature_2m":[%s],
                           "precipitation_probability":[%s],
                           "soil_moisture_0_to_1cm":[%s]},
                 "otroCampo":"x"}
                """.formatted(horas, String.join(",", temperatura), String.join(",", lluvia), String.join(",", humedad));
    }

    private static List<String> constante(String valor) {
        return Collections.nCopies(24, valor);
    }

    private static List<String> conValorEn(String base, int hora, String valor) {
        List<String> lista = new java.util.ArrayList<>(constante(base));
        lista.set(hora, valor);
        return lista;
    }

    @Test
    void pideLosDatosHorariosDeLaFechaDeLaOrden() {
        openMeteo.expect(anything()).andExpect(request -> {
            String uri = request.getURI().toString();
            assertTrue(uri.startsWith("http://open-meteo.test/v1/forecast?"));
            assertTrue(uri.contains("latitude=-31.245"));
            assertTrue(uri.contains("hourly=temperature_2m,precipitation_probability,soil_moisture_0_to_1cm"));
            assertTrue(uri.contains("start_date=2026-11-20"));
            assertTrue(uri.contains("end_date=2026-11-20"));
            assertTrue(uri.contains("timezone=auto"));
        }).andRespond(withSuccess(respuesta(constante("20"), constante("10"), constante("0.2")), MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);

        openMeteo.verify();
    }

    @Test
    void tomaElPeorValorDeLaVentana() {
        // dentro de la ventana: a las 10 llueve más, a las 12 el suelo está más húmedo y a las 8 hace más frío
        List<String> temperatura = conValorEn("20", 8, "11.5");
        List<String> lluvia = conValorEn("10", 10, "70");
        List<String> humedad = conValorEn("0.20", 12, "0.45");
        openMeteo.expect(anything()).andRespond(withSuccess(respuesta(temperatura, lluvia, humedad), MediaType.APPLICATION_JSON));

        DatosClimaDTO datos = service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);

        assertEquals(11.5, datos.getTemperatura());
        assertEquals(70.0, datos.getProbabilidadLluvia());
        assertEquals(0.45, datos.getHumedadSuelo());
    }

    @Test
    void ignoraLasHorasFueraDeLaVentana() {
        // 07:00 (antes) y 14:00 (después) son malos, pero no forman parte de la jornada de 08:00 a 13:00
        List<String> temperatura = new java.util.ArrayList<>(conValorEn("20", 7, "-5"));
        temperatura.set(14, "-5");
        List<String> lluvia = new java.util.ArrayList<>(constante("10"));
        lluvia.set(7, "100");
        lluvia.set(14, "100");
        openMeteo.expect(anything()).andRespond(withSuccess(respuesta(temperatura, lluvia, constante("0.2")), MediaType.APPLICATION_JSON));

        DatosClimaDTO datos = service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);

        assertEquals(20.0, datos.getTemperatura());
        assertEquals(10.0, datos.getProbabilidadLluvia());
    }

    @Test
    void laVentanaYLaHoraDeInicioSonConfigurables() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        // jornada de 2 horas desde las 14: solo cuentan 14:00 y 15:00
        OpenMeteoService corta = new OpenMeteoService(builder.build(), "http://open-meteo.test/v1/forecast",
                Duration.ofMinutes(15), Clock.systemDefaultZone(), 2, 14);
        List<String> lluvia = new java.util.ArrayList<>(constante("90"));
        lluvia.set(14, "5");
        lluvia.set(15, "15");
        servidor.expect(anything()).andRespond(withSuccess(respuesta(constante("20"), lluvia, constante("0.2")), MediaType.APPLICATION_JSON));

        assertEquals(15.0, corta.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA).getProbabilidadLluvia());
    }

    @Test
    void unaVentanaQueSeSaleDelDiaSeRechazaAlConfigurar() {
        assertThrows(IllegalArgumentException.class, () -> new OpenMeteoService(RestClient.builder().build(),
                "http://open-meteo.test", Duration.ofMinutes(15), Clock.systemDefaultZone(), 6, 20));
    }

    @Test
    void sinProbabilidadDeLluviaEnUnaHoraDeLaVentanaFallaEnVezDeAsumirCero() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta(constante("20"), conValorEn("10", 9, "null"), constante("0.2")), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
    }

    @Test
    void sinHumedadDeSueloFallaEnVezDeAsumirCero() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta(constante("20"), constante("10"), conValorEn("0.2", 13, "null")), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
    }

    @Test
    void sinTemperaturaFalla() {
        openMeteo.expect(anything()).andRespond(withSuccess(
                respuesta(conValorEn("20", 11, "null"), constante("10"), constante("0.2")), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
    }

    @Test
    void siFaltanHorasDeLaVentanaFalla() {
        String incompleta = """
                {"hourly":{"time":["2026-11-20T08:00","2026-11-20T09:00"],
                           "temperature_2m":[20,20],"precipitation_probability":[10,10],"soil_moisture_0_to_1cm":[0.2,0.2]}}
                """;
        openMeteo.expect(anything()).andRespond(withSuccess(incompleta, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
    }

    @Test
    void siOpenMeteoFallaSePropagaElError() {
        openMeteo.expect(anything()).andRespond(withServerError());

        assertThrows(RuntimeException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
    }
}
