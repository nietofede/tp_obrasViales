package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.FaseEnRiesgoRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Cancelacion y reprogramacion de ordenes (RN-11) con las dependencias simuladas.
@ExtendWith(MockitoExtension.class)
class OrdenTrabajoServiceCancelacionTest {

    private static final Long ORDEN_ID = 10L;
    private static final Long FASE_ID = 2L;

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private SuspensionService suspensionService;
    @Mock private FaseEnRiesgoRepository faseEnRiesgoRepository;

    @InjectMocks private OrdenTrabajoServiceImpl service;

    private OrdenTrabajo ordenEn(EstadoOrden estado) {
        OrdenTrabajo orden = new OrdenTrabajo();
        orden.setId(ORDEN_ID);
        orden.setFaseId(FASE_ID);
        orden.setTramoId(1L);
        orden.setFechaProgramada(LocalDate.of(2026, 11, 20));
        orden.setEstado(estado);
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.of(orden));
        return orden;
    }

    @Test
    void cancelaUnaOrdenConRecursosYLiberaSuReserva() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.cancelarOrden(ORDEN_ID);

        assertEquals(EstadoOrden.CANCELADA, dto.getEstado());
        verify(suspensionService).liberarReserva(orden);
    }

    @Test
    void cancelaUnaOrdenProgramada() {
        ordenEn(EstadoOrden.PROGRAMADA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(EstadoOrden.CANCELADA, service.cancelarOrden(ORDEN_ID).getEstado());
    }

    @Test
    void noSePuedeCancelarUnaOrdenEnEjecucion() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.EN_EJECUCION);

        assertThrows(TransicionInvalida.class, () -> service.cancelarOrden(ORDEN_ID));

        assertEquals(EstadoOrden.EN_EJECUCION, orden.getEstado());
        verify(suspensionService, never()).liberarReserva(any());
    }

    @Test
    void siLaOrdenDejoALaFaseEnRiesgoLaDecisionVaPorOtroEndpoint() {
        ordenEn(EstadoOrden.SUSPENDIDA);
        when(faseEnRiesgoRepository.existsByFaseIdAndResueltaFalse(FASE_ID)).thenReturn(true);

        assertThrows(ConflictoEstadoException.class, () -> service.cancelarOrden(ORDEN_ID));

        verify(ordenRepository, never()).save(any());
    }

    @Test
    void cancelarUnaOrdenInexistenteDa404() {
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.empty());

        assertThrows(OrdenNoEncontrada.class, () -> service.cancelarOrden(ORDEN_ID));
    }

    @Test
    void reprogramaUnaOrdenProgramadaAOtraFecha() {
        ordenEn(EstadoOrden.PROGRAMADA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.reprogramarOrden(ORDEN_ID, LocalDate.of(2026, 11, 25));

        assertEquals(LocalDate.of(2026, 11, 25), dto.getFechaProgramada());
        assertEquals(EstadoOrden.PROGRAMADA, dto.getEstado());
    }

    @Test
    void soloSeReprogramaUnaOrdenSinRecursos() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);

        assertThrows(TransicionInvalida.class, () -> service.reprogramarOrden(ORDEN_ID, LocalDate.of(2026, 11, 25)));

        assertEquals(LocalDate.of(2026, 11, 20), orden.getFechaProgramada());
        verify(ordenRepository, never()).save(any());
    }
}
