package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.ResultadoHabilitacionDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los escenarios de negocio (habilitada, suspendida, rechazo de Recursos, Obras caido, RN-01) se prueban en
// OrdenTrabajoServiceHabilitacionTest y HabilitacionDiariaServiceTest. Aca solo se prueba que el job delega.
@ExtendWith(MockitoExtension.class)
class JobHabilitacionDiariaTest {

    @Mock
    private HabilitacionDiariaService habilitacionDiariaService;

    @InjectMocks
    private JobHabilitacionDiaria job;

    @Test
    void elJobProgramadoDelegaEnLaHabilitacionDiariaConLaFechaDeHoy() {
        LocalDate hoy = LocalDate.now();
        when(habilitacionDiariaService.habilitarDelDia(any())).thenReturn(List.of());

        job.ejecutarEvaluacionDiaria();

        verify(habilitacionDiariaService).habilitarDelDia(hoy);
    }

    @Test
    void devuelveLosResultadosDeCadaOrden() {
        LocalDate fecha = LocalDate.of(2026, 11, 12);
        List<ResultadoHabilitacionDTO> resultados = List.of(
                new ResultadoHabilitacionDTO(1L, EstadoOrden.HABILITADA, null),
                new ResultadoHabilitacionDTO(2L, EstadoOrden.REPROGRAMADA, null),
                new ResultadoHabilitacionDTO(3L, EstadoOrden.PROGRAMADA, "Recursos rechazó la reserva."));
        when(habilitacionDiariaService.habilitarDelDia(fecha)).thenReturn(resultados);

        assertThat(job.ejecutar(fecha)).isEqualTo(resultados);
    }

    @Test
    void unFalloGeneralNoTiraElScheduler() {
        LocalDate fecha = LocalDate.of(2026, 11, 12);
        when(habilitacionDiariaService.habilitarDelDia(fecha)).thenThrow(new IllegalStateException("base caída"));

        assertThat(job.ejecutar(fecha)).isEmpty();
    }
}
