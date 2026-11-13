package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.clientes.RecursosClient;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.FaseDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import com.obrasviales.ms_operaciones.modelo.ConfiguracionSuspensiones;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import com.obrasviales.ms_operaciones.modelo.MotivoSuspension;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.Suspension;
import com.obrasviales.ms_operaciones.repositorios.ConfiguracionSuspensionesRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// RN-12 y saga de suspension con Obras y Recursos simulados.
@ExtendWith(MockitoExtension.class)
class SuspensionServiceTest {

    private static final Long ORDEN_ID = 10L;
    private static final Long FASE_ID = 2L;
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 13);

    @Mock private SuspensionRepository suspensionRepository;
    @Mock private ConfiguracionSuspensionesRepository configuracionRepository;
    @Mock private FaseEnRiesgoRepository faseEnRiesgoRepository;
    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private RecursosClient recursosClient;
    @Mock private ObrasClient obrasClient;

    @InjectMocks private SuspensionService service;

    private OrdenTrabajo orden;

    @BeforeEach
    void preparar() {
        orden = new OrdenTrabajo();
        orden.setId(ORDEN_ID);
        orden.setFaseId(FASE_ID);
        orden.setTramoId(1L);
        orden.setFechaProgramada(FECHA);
        orden.setEstado(EstadoOrden.EVALUACION_CLIMATICA);
        orden.setReservaId(7L);
    }

    private void limite(int n) {
        ConfiguracionSuspensiones config = new ConfiguracionSuspensiones();
        config.setLimiteSuspensiones(n);
        when(configuracionRepository.findById(ConfiguracionSuspensiones.ID_UNICO)).thenReturn(Optional.of(config));
    }

    private FaseDTO faseReprogramada(LocalDate inicio) {
        return new FaseDTO(FASE_ID, 1L, "BASE", "PENDIENTE", inicio, inicio.plusDays(5), null, null);
    }

    private void finFaseAnterior(LocalDate fin) {
        when(obrasClient.obtenerContextoFase(FASE_ID)).thenReturn(new ContextoFaseDTO(FASE_ID, "BASE", "PENDIENTE", true,
                1L, "Tramo 1", null, -31.245, -64.456, List.of(), fin));
    }

    @Test
    void siLaFaseAnteriorTerminaDespuesReprogramaAlFinDeEsaFase() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);
        LocalDate finAnterior = FECHA.plusDays(4);
        finFaseAnterior(finAnterior);
        when(obrasClient.reprogramarFase(FASE_ID, finAnterior)).thenReturn(faseReprogramada(finAnterior));

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        ArgumentCaptor<OrdenTrabajo> nueva = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(ordenRepository).save(nueva.capture());
        assertEquals(finAnterior, nueva.getValue().getFechaProgramada());
        assertEquals(EstadoOrden.REPROGRAMADA, orden.getEstado());
    }

    @Test
    void siLaFaseAnteriorTerminaAntesReprogramaAlDiaSiguiente() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);
        finFaseAnterior(FECHA.minusDays(3));
        when(obrasClient.reprogramarFase(FASE_ID, FECHA.plusDays(1))).thenReturn(faseReprogramada(FECHA.plusDays(1)));

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        verify(obrasClient).reprogramarFase(FASE_ID, FECHA.plusDays(1));
    }

    @Test
    void debajoDelLimiteLiberaReprogramaLaFaseYCreaLaOrdenNueva() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);
        finFaseAnterior(null);
        when(obrasClient.reprogramarFase(FASE_ID, FECHA.plusDays(1))).thenReturn(faseReprogramada(FECHA.plusDays(1)));

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        verify(recursosClient).liberar(7L);
        assertNull(orden.getReservaId());
        assertEquals(EstadoOrden.REPROGRAMADA, orden.getEstado());
        assertEquals(1, orden.getContadorSuspensiones());

        ArgumentCaptor<Suspension> suspension = ArgumentCaptor.forClass(Suspension.class);
        verify(suspensionRepository).save(suspension.capture());
        assertEquals(MotivoSuspension.CLIMA, suspension.getValue().getMotivo());
        assertEquals(FASE_ID, suspension.getValue().getFaseId());

        ArgumentCaptor<OrdenTrabajo> nueva = ArgumentCaptor.forClass(OrdenTrabajo.class);
        verify(ordenRepository).save(nueva.capture());
        assertEquals(EstadoOrden.PROGRAMADA, nueva.getValue().getEstado());
        assertEquals(FASE_ID, nueva.getValue().getFaseId());
        assertEquals(FECHA.plusDays(1), nueva.getValue().getFechaProgramada());
        verify(obrasClient, never()).marcarFaseEnRiesgo(anyLong());
    }

    @Test
    void alLlegarAlLimiteMarcaLaFaseEnRiesgoYNoCreaOrdenNueva() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(3L);
        when(faseEnRiesgoRepository.existsByFaseIdAndResueltaFalse(FASE_ID)).thenReturn(false);

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        verify(recursosClient).liberar(7L);
        verify(obrasClient).marcarFaseEnRiesgo(FASE_ID);
        verify(obrasClient, never()).reprogramarFase(anyLong(), any());
        verify(ordenRepository, never()).save(any());
        assertEquals(EstadoOrden.SUSPENDIDA, orden.getEstado());

        ArgumentCaptor<FaseEnRiesgo> enRiesgo = ArgumentCaptor.forClass(FaseEnRiesgo.class);
        verify(faseEnRiesgoRepository).save(enRiesgo.capture());
        assertEquals(FASE_ID, enRiesgo.getValue().getFaseId());
        assertEquals(ORDEN_ID, enRiesgo.getValue().getOrdenTrabajoId());
    }

    @Test
    void siLaFaseYaEstabaEnRiesgoPendienteNoSeDuplica() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(4L);
        when(faseEnRiesgoRepository.existsByFaseIdAndResueltaFalse(FASE_ID)).thenReturn(true);

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        verify(faseEnRiesgoRepository, never()).save(any());
    }

    @Test
    void elLimiteEsConfigurable() {
        limite(1);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        verify(obrasClient).marcarFaseEnRiesgo(FASE_ID);
    }

    @Test
    void siNoSePuedeLiberarLaReservaLaSuspensionSigueAdelante() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);
        when(recursosClient.liberar(7L)).thenThrow(new ServicioNoDisponibleException("No se pudo consultar a ms-recursos."));
        finFaseAnterior(null);
        when(obrasClient.reprogramarFase(FASE_ID, FECHA.plusDays(1))).thenReturn(faseReprogramada(FECHA.plusDays(1)));

        service.suspender(orden, MotivoSuspension.CLIMA, "lluvia");

        assertEquals(EstadoOrden.REPROGRAMADA, orden.getEstado());
        assertNull(orden.getReservaId());
    }

    @Test
    void siObrasRechazaLaReprogramacionSePropagaElErrorYNoSeCreaLaOrdenNueva() {
        limite(3);
        when(suspensionRepository.countByFaseIdAndVigenteTrue(FASE_ID)).thenReturn(1L);
        finFaseAnterior(null);
        when(obrasClient.reprogramarFase(FASE_ID, FECHA.plusDays(1)))
                .thenThrow(new ReglaNegocioException("La nueva fecha es anterior al fin de la fase previa.", "RN-02"));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.suspender(orden, MotivoSuspension.CLIMA, "lluvia"));

        assertEquals("RN-02", ex.getCodigoRegla());
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void actualizaElLimiteConfigurado() {
        ConfiguracionSuspensiones config = new ConfiguracionSuspensiones();
        config.setLimiteSuspensiones(3);
        when(configuracionRepository.findById(ConfiguracionSuspensiones.ID_UNICO)).thenReturn(Optional.of(config));
        when(configuracionRepository.save(config)).thenReturn(config);

        assertEquals(5, service.actualizarLimite(5));
    }
}
