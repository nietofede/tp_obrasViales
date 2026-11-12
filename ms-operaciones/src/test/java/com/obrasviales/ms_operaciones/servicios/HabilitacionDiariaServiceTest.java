package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.dtos.ResultadoHabilitacionDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabilitacionDiariaServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 13);

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private OTServiceInterfaz ordenService;

    @InjectMocks private HabilitacionDiariaService service;

    private OrdenTrabajo orden(Long id, EstadoOrden estado) {
        OrdenTrabajo o = new OrdenTrabajo();
        o.setId(id);
        o.setEstado(estado);
        return o;
    }

    private OrdenTrabajoDTO resultado(EstadoOrden estado) {
        OrdenTrabajoDTO dto = new OrdenTrabajoDTO();
        dto.setEstado(estado);
        return dto;
    }

    @Test
    void unaOrdenQueFallaNoFrenaALasDemasYSeInformaSuMotivo() {
        when(ordenRepository.findByFechaProgramadaAndEstado(FECHA, EstadoOrden.PROGRAMADA))
                .thenReturn(List.of(orden(1L, EstadoOrden.PROGRAMADA), orden(2L, EstadoOrden.PROGRAMADA)));
        when(ordenRepository.findByFechaProgramadaAndEstado(FECHA, EstadoOrden.RECURSOS_ASIGNADOS))
                .thenReturn(List.of(orden(3L, EstadoOrden.RECURSOS_ASIGNADOS)));
        when(ordenService.habilitarOrden(1L)).thenReturn(resultado(EstadoOrden.HABILITADA));
        when(ordenService.habilitarOrden(2L)).thenThrow(new ReglaNegocioException("Ninguna cuadrilla es elegible.", "RN-07"));
        when(ordenService.habilitarOrden(3L)).thenReturn(resultado(EstadoOrden.SUSPENDIDA));

        List<ResultadoHabilitacionDTO> r = service.habilitarDelDia(FECHA);

        assertEquals(3, r.size());
        assertEquals(EstadoOrden.HABILITADA, r.get(0).estado());
        assertNull(r.get(0).motivo());
        assertEquals(EstadoOrden.PROGRAMADA, r.get(1).estado());
        assertEquals("Ninguna cuadrilla es elegible.", r.get(1).motivo());
        assertEquals(EstadoOrden.SUSPENDIDA, r.get(2).estado());
    }

    @Test
    void unErrorInesperadoNoFiltraSuDetalle() {
        when(ordenRepository.findByFechaProgramadaAndEstado(FECHA, EstadoOrden.PROGRAMADA))
                .thenReturn(List.of(orden(1L, EstadoOrden.PROGRAMADA)));
        when(ordenRepository.findByFechaProgramadaAndEstado(FECHA, EstadoOrden.RECURSOS_ASIGNADOS)).thenReturn(List.of());
        when(ordenService.habilitarOrden(1L)).thenThrow(new IllegalStateException("detalle interno"));

        List<ResultadoHabilitacionDTO> r = service.habilitarDelDia(FECHA);

        assertEquals("Error inesperado al habilitar la orden.", r.get(0).motivo());
    }
}
