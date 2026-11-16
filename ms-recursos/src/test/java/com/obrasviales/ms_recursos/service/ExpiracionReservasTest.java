package com.obrasviales.ms_recursos.service;

import com.obrasviales.ms_recursos.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Expiración de reservas por TTL (el job solo llama a este método cada tanto).
@ExtendWith(MockitoExtension.class)
class ExpiracionReservasTest {

    @Mock
    private ReservaRepository reservaRepository;

    private ReservaService serviceConSoloReservas() {
        // Estos dos métodos solo usan el repositorio de reservas; el resto de las dependencias no se tocan.
        return new ReservaService(reservaRepository, null, null, null, null, null, null, null);
    }

    @Test
    void expirarVencidasDevuelveCuantasCambio() {
        when(reservaRepository.expirarReservasVencidas(any(LocalDateTime.class))).thenReturn(3);

        assertThat(serviceConSoloReservas().expirarVencidas()).isEqualTo(3);
    }

    @Test
    void elJobDelegaEnElService() {
        ReservaService reservaService = mock(ReservaService.class);
        when(reservaService.expirarVencidas()).thenReturn(2);

        new ExpiracionReservasJob(reservaService).expirarVencidas();

        verify(reservaService, times(1)).expirarVencidas();
    }

    @Test
    void unFalloEnElJobNoSePropagaParaNoFrenarLasProximasCorridas() {
        ReservaService reservaService = mock(ReservaService.class);
        when(reservaService.expirarVencidas()).thenThrow(new IllegalStateException("base caída"));

        assertThatCode(() -> new ExpiracionReservasJob(reservaService).expirarVencidas())
                .doesNotThrowAnyException();
    }
}
