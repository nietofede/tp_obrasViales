package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.DatosClimaDTO;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.excepciones.ServicioNoDisponibleException;
import com.obrasviales.ms_operaciones.excepciones.TransicionInvalida;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.modelo.UmbralClimatico;
import com.obrasviales.ms_operaciones.repositorios.EvaluacionClimaticaRepository;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import com.obrasviales.ms_operaciones.repositorios.UmbralClimaticoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Habilitacion de ordenes con ms-obras y el clima simulados: no hace falta base de datos ni servicios levantados.
@ExtendWith(MockitoExtension.class)
class OrdenTrabajoServiceHabilitacionTest {

    private static final Long ORDEN_ID = 10L;
    private static final Long FASE_ID = 2L;

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private UmbralClimaticoRepository umbralRepository;
    @Mock private EvaluacionClimaticaRepository evaluacionRepository;
    @Mock private InterfazClimaService climaService;
    @Mock private ObrasClient obrasClient;

    @InjectMocks private OrdenTrabajoServiceImpl service;

    private OrdenTrabajo ordenEn(EstadoOrden estado) {
        OrdenTrabajo orden = new OrdenTrabajo();
        orden.setId(ORDEN_ID);
        orden.setFaseId(FASE_ID);
        orden.setTramoId(1L);
        orden.setFechaProgramada(LocalDate.now().plusDays(1));
        orden.setEstado(estado);
        when(ordenRepository.findById(ORDEN_ID)).thenReturn(Optional.of(orden));
        return orden;
    }

    private ContextoFaseDTO contexto(boolean faseAnteriorCompletada, String tipoTrabajo) {
        return new ContextoFaseDTO(FASE_ID, tipoTrabajo, "PENDIENTE", faseAnteriorCompletada, 1L, "Tramo 1",
                new BigDecimal("5000.00"), -31.245, -64.456, List.of());
    }

    private UmbralClimatico umbralBase() {
        UmbralClimatico u = new UmbralClimatico();
        u.setTipoTrabajo(TipoTrabajo.BASE);
        u.setHumedadSueloMaxima(0.40);
        u.setProbPrecipitacionMaxima(20.0);
        u.setTemperaturaMinima(5.0);
        return u;
    }

    @Test
    void habilitaLaOrdenUsandoLasCoordenadasQueVienenDeObras() {
        ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);
        when(obrasClient.obtenerContextoFase(FASE_ID)).thenReturn(contexto(true, "BASE"));
        when(umbralRepository.findByTipoTrabajo(TipoTrabajo.BASE)).thenReturn(Optional.of(umbralBase()));
        when(climaService.obtenerCondicionesClimaticas(-31.245, -64.456)).thenReturn(
                DatosClimaDTO.builder().humedadSuelo(0.20).probabilidadLluvia(5.0).temperatura(18.0).build());
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.habilitarOrden(ORDEN_ID);

        assertEquals(EstadoOrden.HABILITADA, dto.getEstado());
        verify(evaluacionRepository).save(any(EvaluacionClimatica.class));
    }

    @Test
    void rn01RechazaSiLaFasePreviaNoEstaCompleta() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);
        when(obrasClient.obtenerContextoFase(FASE_ID)).thenReturn(contexto(false, "BASE"));

        ConflictoEstadoException ex = assertThrows(ConflictoEstadoException.class,
                () -> service.habilitarOrden(ORDEN_ID));

        assertTrue(ex.getMessage().startsWith("RN-01"));
        assertEquals(EstadoOrden.RECURSOS_ASIGNADOS, orden.getEstado());
        verify(climaService, never()).obtenerCondicionesClimaticas(any(), any());
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void rn11RechazaSiLaOrdenNoTieneRecursosAsignadosYNoConsultaAObras() {
        ordenEn(EstadoOrden.PROGRAMADA);

        assertThrows(TransicionInvalida.class, () -> service.habilitarOrden(ORDEN_ID));

        verify(obrasClient, never()).obtenerContextoFase(any());
    }

    @Test
    void rechazaUnTipoDeTrabajoQueNoConoce() {
        ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);
        when(obrasClient.obtenerContextoFase(FASE_ID)).thenReturn(contexto(true, "TIPO_RARO"));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.habilitarOrden(ORDEN_ID));

        assertEquals("RN-09", ex.getCodigoRegla());
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void siObrasFallaLaOrdenNoCambiaDeEstado() {
        OrdenTrabajo orden = ordenEn(EstadoOrden.RECURSOS_ASIGNADOS);
        when(obrasClient.obtenerContextoFase(FASE_ID)).thenThrow(new ServicioNoDisponibleException("No se pudo consultar a ms-obras."));

        assertThrows(ServicioNoDisponibleException.class, () -> service.habilitarOrden(ORDEN_ID));

        assertEquals(EstadoOrden.RECURSOS_ASIGNADOS, orden.getEstado());
        verify(ordenRepository, never()).save(any());
    }
}
