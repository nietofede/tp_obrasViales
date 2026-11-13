package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.ContextoFaseDTO;
import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Alta de ordenes con ms-obras simulado: no hace falta base de datos ni Obras levantado.
@ExtendWith(MockitoExtension.class)
class OrdenTrabajoServiceCrearOrdenTest {

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private UmbralClimaticoRepository umbralRepository;
    @Mock private EvaluacionClimaticaRepository evaluacionRepository;
    @Mock private InterfazClimaService climaService;
    @Mock private ObrasClient obrasClient;

    @InjectMocks private OrdenTrabajoServiceImpl service;

    private CrearOrdenRequest solicitud(Long faseId, Long tramoId) {
        CrearOrdenRequest r = new CrearOrdenRequest();
        r.setFaseId(faseId);
        r.setTramoId(tramoId);
        r.setFechaProgramada(LocalDate.now().plusDays(1));
        return r;
    }

    private ContextoFaseDTO contexto(Long faseId, Long tramoId) {
        return new ContextoFaseDTO(faseId, "BASE", "PENDIENTE", true, tramoId, "Tramo 1",
                new BigDecimal("5000.00"), -31.245, -64.456, List.of(), null);
    }

    @Test
    void creaLaOrdenProgramadaCuandoLaFaseExisteYPerteneceAlTramo() {
        when(obrasClient.obtenerContextoFase(2L)).thenReturn(contexto(2L, 1L));
        when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenTrabajoDTO dto = service.crearOrden(solicitud(2L, 1L));

        assertEquals(EstadoOrden.PROGRAMADA, dto.getEstado());
        assertEquals(2L, dto.getFaseId());
        assertEquals(1L, dto.getTramoId());
    }

    @Test
    void rechazaSiLaFaseNoPerteneceAlTramoIndicado() {
        when(obrasClient.obtenerContextoFase(2L)).thenReturn(contexto(2L, 1L));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.crearOrden(solicitud(2L, 99L)));

        assertEquals("ORDEN-TRAMO", ex.getCodigoRegla());
        verify(ordenRepository, never()).save(any());
    }

    @Test
    void noGuardaNadaSiLaFaseNoExisteEnObras() {
        when(obrasClient.obtenerContextoFase(9999L))
                .thenThrow(new RecursoNoEncontradoException("La fase 9999 no existe en ms-obras."));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crearOrden(solicitud(9999L, 1L)));

        verify(ordenRepository, never()).save(any());
    }
}
