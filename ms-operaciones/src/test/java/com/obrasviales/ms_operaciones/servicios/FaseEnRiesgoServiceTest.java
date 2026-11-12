package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoRequest;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoRequest.DecisionRiesgo;
import com.obrasviales.ms_operaciones.dtos.DecisionRiesgoResponse;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.FaseEnRiesgoRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.SuspensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// RN-12: decision del PLANIFICADOR sobre una fase en riesgo, con Obras simulado.
@ExtendWith(MockitoExtension.class)
class FaseEnRiesgoServiceTest {

    private static final Long FASE_ID = 2L;
    private static final Long ORDEN_ID = 10L;
    private static final LocalDate NUEVA_FECHA = LocalDate.of(2026, 12, 1);

    @Mock private FaseEnRiesgoRepository faseEnRiesgoRepository;
    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private SuspensionRepository suspensionRepository;
    @Mock private ObrasClient obrasClient;

    @InjectMocks private FaseEnRiesgoService service;

    private FaseEnRiesgo enRiesgo;
    private OrdenTrabajo orden;

    @BeforeEach
    void preparar() {
        enRiesgo = new FaseEnRiesgo();
        enRiesgo.setFaseId(FASE_ID);
        enRiesgo.setOrdenTrabajoId(ORDEN_ID);
        orden = new OrdenTrabajo();
        orden.setId(ORDEN_ID);
        orden.setFaseId(FASE_ID);
        orden.setTramoId(1L);
        orden.setEstado(EstadoOrden.SUSPENDIDA);
    }

    private void hayDecisionPendiente() {
        when(faseEnRiesgoRepository.findByFaseIdAndResueltaFalse(FASE_ID)).thenReturn(Optional.of(enRiesgo));
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.of(orden));
    }

    @Test
    void reprogramarLlamaAObrasCreaLaOrdenNuevaYReiniciaElConteo() {
        hayDecisionPendiente();
        when(obrasClient.reprogramarFase(FASE_ID, NUEVA_FECHA)).thenReturn(
                new FaseDTO(FASE_ID, 1L, "BASE", "PENDIENTE", NUEVA_FECHA, NUEVA_FECHA.plusDays(5), null, null));
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> {
            OrdenTrabajo o = inv.getArgument(0);
            if (o.getId() == null) o.setId(11L);
            return o;
        });

        DecisionRiesgoResponse r = service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.REPROGRAMAR, NUEVA_FECHA));

        assertEquals(EstadoOrden.REPROGRAMADA, r.estadoOrden());
        assertEquals(11L, r.nuevaOrdenId());
        assertEquals(EstadoOrden.REPROGRAMADA, orden.getEstado());
        verify(suspensionRepository).marcarNoVigentes(FASE_ID);
        assertTrue(enRiesgo.isResuelta());
        assertEquals("REPROGRAMAR", enRiesgo.getDecision());

        ArgumentCaptor<OrdenTrabajo> guardadas = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(ordenRepository, org.mockito.Mockito.times(2)).save(guardadas.capture());
        OrdenTrabajo nueva = guardadas.getAllValues().get(0);
        assertEquals(EstadoOrden.PROGRAMADA, nueva.getEstado());
        assertEquals(NUEVA_FECHA, nueva.getFechaProgramada());
    }

    @Test
    void cancelarDejaLaOrdenCanceladaSinTocarAObras() {
        hayDecisionPendiente();
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionRiesgoResponse r = service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.CANCELAR, null));

        assertEquals(EstadoOrden.CANCELADA, r.estadoOrden());
        assertNull(r.nuevaOrdenId());
        verify(obrasClient, never()).reprogramarFase(anyLong(), any());
        verify(suspensionRepository).marcarNoVigentes(FASE_ID);
        assertEquals("CANCELAR", enRiesgo.getDecision());
    }

    @Test
    void reprogramarSinFechaEsUnaReglaIncumplida() {
        hayDecisionPendiente();

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.REPROGRAMAR, null)));

        assertEquals("DECISION-FECHA", ex.getCodigoRegla());
        verify(obrasClient, never()).reprogramarFase(anyLong(), any());
    }

    @Test
    void sinDecisionPendienteDa404() {
        when(faseEnRiesgoRepository.findByFaseIdAndResueltaFalse(FASE_ID)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.CANCELAR, null)));
    }

    @Test
    void siLaOrdenYaNoEstaSuspendidaDa409() {
        orden.setEstado(EstadoOrden.CANCELADA);
        hayDecisionPendiente();

        assertThrows(ConflictoEstadoException.class,
                () -> service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.CANCELAR, null)));
    }

    @Test
    void siObrasRechazaLaReprogramacionNoSeResuelveNada() {
        hayDecisionPendiente();
        when(obrasClient.reprogramarFase(FASE_ID, NUEVA_FECHA))
                .thenThrow(new ReglaNegocioException("Fecha anterior al fin de la fase previa.", "RN-02"));

        assertThrows(ReglaNegocioException.class,
                () -> service.decidir(FASE_ID, new DecisionRiesgoRequest(DecisionRiesgo.REPROGRAMAR, NUEVA_FECHA)));

        assertEquals(false, enRiesgo.isResuelta());
        verify(suspensionRepository, never()).marcarNoVigentes(anyLong());
    }

    @Test
    void listaLasFasesPendientes() {
        when(faseEnRiesgoRepository.findByResueltaFalseOrderByCreadoAsc()).thenReturn(List.of(enRiesgo));

        assertEquals(1, service.listarPendientes().size());
        assertEquals(FASE_ID, service.listarPendientes().get(0).faseId());
    }
}
