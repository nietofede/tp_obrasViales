package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Cache del ultimo pronostico: se reutiliza mientras no vence el TTL y nunca se guarda una respuesta incompleta.
class OpenMeteoServiceCacheTest {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 13);
    private static final String RESPUESTA = jornada("0.32");
    private static final String SIN_HUMEDAD = jornada("null");

    // ventana por defecto: de 08:00 a 13:00 de la fecha de la orden
    private static String jornada(String humedad) {
        String horas = IntStream.range(8, 14).mapToObj(h -> "\"%sT%02d:00\"".formatted(FECHA, h))
                .collect(Collectors.joining(","));
        return "{\"hourly\":{\"time\":[" + horas + "],"
                + "\"temperature_2m\":[" + seisVeces("17.5") + "],"
                + "\"precipitation_probability\":[" + seisVeces("35") + "],"
                + "\"soil_moisture_0_to_1cm\":[" + seisVeces(humedad) + "]}}";
    }

    private static String seisVeces(String valor) {
        return String.join(",", Collections.nCopies(6, valor));
    }

    private MockRestServiceServer openMeteo;
    private RelojManual reloj;
    private OpenMeteoService service;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        openMeteo = MockRestServiceServer.bindTo(builder).build();
        reloj = new RelojManual(Instant.parse("2026-11-13T09:00:00Z"));
        service = new OpenMeteoService(builder.build(), "http://open-meteo.test/v1/forecast", TTL, reloj);
    }

    @Test
    void dentroDelTtlReutilizaElPronosticoSinLlamarDeNuevo() {
        openMeteo.expect(once(), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        DatosClimaDTO primero = service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);
        reloj.avanzar(Duration.ofMinutes(10));
        DatosClimaDTO segundo = service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);

        assertThat(segundo).isEqualTo(primero);
        openMeteo.verify();
    }

    @Test
    void vencidoElTtlVuelveAConsultar() {
        openMeteo.expect(times(2), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);
        reloj.avanzar(TTL.plusSeconds(1));
        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);

        openMeteo.verify();
    }

    @Test
    void otraCoordenadaNoUsaLaCache() {
        openMeteo.expect(times(2), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);
        service.obtenerCondicionesClimaticas(-33.130, -64.350, FECHA);

        openMeteo.verify();
    }

    @Test
    void otraFechaNoUsaLaCache() {
        openMeteo.expect(times(2), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);
        // la otra fecha pide su propio pronostico (la respuesta simulada no trae sus horas, por eso falla)
        assertThrows(IllegalStateException.class,
                () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA.plusDays(1)));

        openMeteo.verify();
    }

    @Test
    void unaRespuestaIncompletaNoSeGuarda() {
        openMeteo.expect(once(), anything()).andRespond(withSuccess(SIN_HUMEDAD, MediaType.APPLICATION_JSON));
        openMeteo.expect(once(), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));
        assertThat(service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA).getHumedadSuelo()).isEqualTo(0.32);

        openMeteo.verify();
    }

    @Test
    void vencidoYConOpenMeteoCaidoNoUsaElPronosticoViejo() {
        openMeteo.expect(once(), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));
        openMeteo.expect(once(), anything()).andRespond(withServerError());

        service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA);
        reloj.avanzar(TTL.plusSeconds(1));
        // vencido: se consulta y falla; el error se propaga (la orden pasa a revision manual), no se usa el viejo
        assertThrows(RuntimeException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456, FECHA));

        openMeteo.verify();
    }

    private static final class RelojManual extends Clock {
        private Instant ahora;

        RelojManual(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
