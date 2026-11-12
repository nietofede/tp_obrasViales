package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.clientes.RecursosClient;
import com.obrasviales.ms_operaciones.dtos.AvanceOrdenResponse;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ResolucionClimaticaRequest;
import com.obrasviales.ms_operaciones.dtos.ResolucionClimaticaRequest.Decision;
import com.obrasviales.ms_operaciones.excepciones.OrdenNoEncontrada;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Ejecucion de ordenes con la saga y Obras simulados.
@ExtendWith(MockitoExtension.class)
class EjecucionServiceTest {

    private static final Long ORDEN_ID = 10L;
    private static final Long FASE_ID = 2L;

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private SuspensionService suspensionService;
    @Mock private ObrasClient obrasClient;
    @Mock private RecursosClient recursosClient;

    @InjectMocks private EjecucionService service;

    private OrdenTrabajo ordenEn(EstadoOrden estado) {
        OrdenTrabajo orden = new OrdenTrabajo();
        orden.setId(ORDEN_ID);
        orden.setFaseId(FASE_ID);
        orden.setTramoId(1L);
        orden.setEstado(estado);
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.of(orden));
        return orden;
    }

    @Test
    void iniciaUnaOrdenHabilitada() {
        ordenEn(EstadoOrden.HABILITADA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.iniciar(ORDEN_ID);

        assertEquals(EstadoOrden.EN_EJECUCION, dto.getEstado());
    }

    @Test
    void noSePuedeIniciarUnaOrdenQueNoEstaHabilitada() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.PROGRAMADA);

        assertThrows(TransicionInvalida.class, () -> service.iniciar(ORDEN_ID));

        assertEquals(EstadoOrden.PROGRAMADA, orden.getEstado());
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void unaOrdenInexistenteDa404() {
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.empty());

        assertThrows(OrdenNoEncontrada.class, () -> service.iniciar(ORDEN_ID));
    }

    @Test
    void elRechazoSuspendeLaOrdenConSuMotivoYDisparaLaSaga() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.HABILITADA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rechazar(ORDEN_ID, "Falta de personal");

        verify(suspensionService).suspender(orden, MotivoSuspension.RECHAZO, "Falta de personal");
    }

    @Test
    void soloSePuedeRechazarUnaOrdenHabilitada() {
        ordenEn(EstadoOrden.EN_EJECUCION);

        assertThrows(TransicionInvalida.class, () -> service.rechazar(ORDEN_ID, "motivo"));

        verify(suspensionService, never()).suspender(any(), any(), any());
    }

    @Test
    void elAvanceSeRegistraEnObrasYDevuelveElEstadoDeLaFase() {
        ordenEn(EstadoOrden.EN_EJECUCION);
        when(obrasClient.registrarAvance(FASE_ID, 40)).thenReturn(
                new FaseDTO(FASE_ID, 1L, "BASE", "EN_CURSO", null, null, null, new BigDecimal("40")));

        AvanceOrdenResponse r = service.registrarAvance(ORDEN_ID, 40);

        assertEquals(new BigDecimal("40"), r.avanceFase());
        assertEquals("EN_CURSO", r.estadoFase());
    }

    @Test
    void noSeRegistraAvanceSiLaOrdenNoEstaEnEjecucion() {
        ordenEn(EstadoOrden.HABILITADA);

        assertThrows(TransicionInvalida.class, () -> service.registrarAvance(ORDEN_ID, 40));

        verify(obrasClient, never()).registrarAvance(anyLong(), anyInt());
    }

    @Test
    void siObrasRechazaElAvanceSeInformaElConflicto() {
        ordenEn(EstadoOrden.EN_EJECUCION);
        when(obrasClient.registrarAvance(FASE_ID, 40)).thenThrow(new ConflictoEstadoException("La fase está en riesgo."));

        assertThrows(ConflictoEstadoException.class, () -> service.registrarAvance(ORDEN_ID, 40));
    }

    @Test
    void habilitarUnaOrdenPendienteDeClimaConfirmaLaReserva() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        orden.setReservaId(7L);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.HABILITAR, null));

        assertEquals(EstadoOrden.HABILITADA, dto.getEstado());
        verify(recursosClient).confirmar(7L);
    }

    @Test
    void suspenderUnaOrdenPendienteDeClimaCorreLaSaga() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.SUSPENDER, "Llovió toda la noche"));

        verify(suspensionService).suspender(orden, MotivoSuspension.CLIMA, "Llovió toda la noche");
        verify(recursosClient, never()).confirmar(anyLong());
    }

    @Test
    void suspenderSinMotivoUsaUnoPorDefecto() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.SUSPENDER, null));

        verify(suspensionService).suspender(eq(orden), eq(MotivoSuspension.CLIMA), anyString());
    }

    @Test
    void soloSeResuelveElClimaDeUnaOrdenPendiente() {
        ordenEn(EstadoOrden.HABILITADA);

        assertThrows(TransicionInvalida.class,
                () -> service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.HABILITAR, null)));

        verify(recursosClient, never()).confirmar(anyLong());
    }

    @Test
    void siLaReservaVencioLaOrdenSigueSinHabilitarse() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);
        orden.setReservaId(7L);
        when(recursosClient.confirmar(7L)).thenThrow(new ConflictoEstadoException("La reserva provisoria ya expiró por TTL"));

        assertThrows(ConflictoEstadoException.class,
                () -> service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.HABILITAR, null)));

        verify(ordenRepository, never()).save(any());
    }

    @Test
    void habilitarSinReservaVigenteDa409() {
        ordenEn(EstadoOrden.PENDIENTE_CONFIRMACION_CLIMATICA);

        assertThrows(ConflictoEstadoException.class,
                () -> service.resolverClima(ORDEN_ID, new ResolucionClimaticaRequest(Decision.HABILITAR, null)));
    }
}
