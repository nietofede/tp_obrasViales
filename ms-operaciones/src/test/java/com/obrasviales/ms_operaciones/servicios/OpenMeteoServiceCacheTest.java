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
import java.time.ZoneId;
import java.time.ZoneOffset;

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
    private static final String RESPUESTA = """
            {"current":{"time":"2026-11-12T22:45","temperature_2m":17.5},
             "hourly":{"time":["2026-11-12T22:00"],"precipitation_probability":[35],"soil_moisture_0_to_1cm":[0.32]}}
            """;
    private static final String SIN_HUMEDAD = """
            {"current":{"time":"2026-11-12T22:45","temperature_2m":17.5},
             "hourly":{"time":["2026-11-12T22:00"],"precipitation_probability":[35],"soil_moisture_0_to_1cm":[null]}}
            """;

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

        DatosClimaDTO primero = service.obtenerCondicionesClimaticas(-31.245, -64.456);
        reloj.avanzar(Duration.ofMinutes(10));
        DatosClimaDTO segundo = service.obtenerCondicionesClimaticas(-31.245, -64.456);

        assertThat(segundo).isEqualTo(primero);
        openMeteo.verify();
    }

    @Test
    void vencidoElTtlVuelveAConsultar() {
        openMeteo.expect(times(2), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456);
        reloj.avanzar(TTL.plusSeconds(1));
        service.obtenerCondicionesClimaticas(-31.245, -64.456);

        openMeteo.verify();
    }

    @Test
    void otraCoordenadaNoUsaLaCache() {
        openMeteo.expect(times(2), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        service.obtenerCondicionesClimaticas(-31.245, -64.456);
        service.obtenerCondicionesClimaticas(-33.130, -64.350);

        openMeteo.verify();
    }

    @Test
    void unaRespuestaIncompletaNoSeGuarda() {
        openMeteo.expect(once(), anything()).andRespond(withSuccess(SIN_HUMEDAD, MediaType.APPLICATION_JSON));
        openMeteo.expect(once(), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));
        assertThat(service.obtenerCondicionesClimaticas(-31.245, -64.456).getHumedadSuelo()).isEqualTo(0.32);

        openMeteo.verify();
    }

    @Test
    void vencidoYConOpenMeteoCaidoNoUsaElPronosticoViejo() {
        openMeteo.expect(once(), anything()).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));
        openMeteo.expect(once(), anything()).andRespond(withServerError());

        service.obtenerCondicionesClimaticas(-31.245, -64.456);
        reloj.avanzar(TTL.plusSeconds(1));
        // vencido: se consulta y falla; el error se propaga (la orden pasa a revision manual), no se usa el viejo
        assertThrows(RuntimeException.class, () -> service.obtenerCondicionesClimaticas(-31.245, -64.456));

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
