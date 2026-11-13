package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_operaciones.clientes.ObrasClient;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Filtros de GET /api/operaciones/ordenes: fecha, estado y obra, solos o combinados.
@ExtendWith(MockitoExtension.class)
class OrdenTrabajoServiceListadoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private ObrasClient obrasClient;

    @InjectMocks private OrdenTrabajoServiceImpl service;

    private static OrdenTrabajo orden(Long id, Long tramoId) {
        OrdenTrabajo orden = new OrdenTrabajo();
        orden.setId(id);
        orden.setFaseId(1L);
        orden.setTramoId(tramoId);
        orden.setFechaProgramada(FECHA);
        orden.setEstado(EstadoOrden.PROGRAMADA);
        return orden;
    }

    private static List<Long> ids(List<OrdenTrabajoDTO> ordenes) {
        return ordenes.stream().map(OrdenTrabajoDTO::getId).toList();
    }

    @Test
    void soloEstadoFiltraPorEstadoSinExigirFecha() {
        when(ordenRepository.findByEstado(EstadoOrden.PROGRAMADA)).thenReturn(List.of(orden(1L, 1L)));

        assertThat(ids(service.listar(null, EstadoOrden.PROGRAMADA, null))).containsExactly(1L);
    }

    @Test
    void soloFechaFiltraPorFecha() {
        when(ordenRepository.findByFechaProgramada(FECHA)).thenReturn(List.of(orden(2L, 1L)));

        assertThat(ids(service.listar(FECHA, null, null))).containsExactly(2L);
    }

    @Test
    void fechaYEstadoSeCombinan() {
        when(ordenRepository.findByFechaProgramadaAndEstado(FECHA, EstadoOrden.PROGRAMADA))
                .thenReturn(List.of(orden(3L, 1L)));

        assertThat(ids(service.listar(FECHA, EstadoOrden.PROGRAMADA, null))).containsExactly(3L);
    }

    @Test
    void sinFiltrosDevuelveTodasYNoConsultaAObras() {
        when(ordenRepository.findAll()).thenReturn(List.of(orden(1L, 1L), orden(2L, 2L)));

        assertThat(ids(service.listar(null, null, null))).containsExactly(1L, 2L);
        verify(obrasClient, never()).tramosDeObra(any());
    }

    @Test
    void obraDejaSoloLasOrdenesDeSusTramos() {
        when(ordenRepository.findAll()).thenReturn(List.of(orden(1L, 1L), orden(2L, 2L), orden(3L, 3L)));
        when(obrasClient.tramosDeObra(7L)).thenReturn(Set.of(1L, 3L));

        assertThat(ids(service.listar(null, null, 7L))).containsExactly(1L, 3L);
    }

    @Test
    void obraSeCombinaConLosOtrosFiltros() {
        when(ordenRepository.findByFechaProgramada(FECHA)).thenReturn(List.of(orden(1L, 1L), orden(2L, 2L)));
        when(obrasClient.tramosDeObra(7L)).thenReturn(Set.of(2L));

        assertThat(ids(service.listar(FECHA, null, 7L))).containsExactly(2L);
    }

    @Test
    void obraInexistenteDevuelveElErrorDeObras() {
        when(ordenRepository.findAll()).thenReturn(List.of(orden(1L, 1L)));
        when(obrasClient.tramosDeObra(99L)).thenThrow(new RecursoNoEncontradoException("La obra 99 no existe en ms-obras."));

        assertThrows(RecursoNoEncontradoException.class, () -> service.listar(null, null, 99L));
    }
}
