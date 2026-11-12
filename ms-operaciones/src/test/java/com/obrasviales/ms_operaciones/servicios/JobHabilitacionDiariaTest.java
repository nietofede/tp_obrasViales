package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.ms_operaciones.dtos.CrearOrdenRequest;
import com.obrasviales.ms_operaciones.dtos.OrdenTrabajoDTO;
import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.repositorios.OrdenTrabajoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class JobHabilitacionDiariaTest {

    @Mock
    private OTServiceInterfaz otService;

    @Mock
    private OrdenTrabajoRepository ordenTrabajoRepository;

    private MockRestServiceServer mockServer;
    private JobHabilitacionDiaria job;

    private final String obrasUrl = "http://localhost:8082";
    private final String recursosUrl = "http://localhost:8083";
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        job = new JobHabilitacionDiaria(otService, restClient, ordenTrabajoRepository);
        ReflectionTestUtils.setField(job, "obrasUrl", obrasUrl);
        ReflectionTestUtils.setField(job, "recursosUrl", recursosUrl);
    }

    private OrdenTrabajoDTO crearOrdenDto(Long id, Long faseId, Long tramoId) {
        OrdenTrabajoDTO dto = new OrdenTrabajoDTO();
        dto.setId(id);
        dto.setFaseId(faseId);
        dto.setTramoId(tramoId);
        dto.setFechaProgramada(hoy);
        dto.setEstado(EstadoOrden.PROGRAMADA);
        return dto;
    }

    private OrdenTrabajo crearOrdenEntidad(Long id, Long faseId, Long tramoId) {
        OrdenTrabajo entidad = new OrdenTrabajo();
        entidad.setId(id);
        entidad.setFaseId(faseId);
        entidad.setTramoId(tramoId);
        entidad.setFechaProgramada(hoy);
        entidad.setEstado(EstadoOrden.PROGRAMADA);
        entidad.setContadorSuspensiones(0);
        return entidad;
    }

    @Test
    @DisplayName("1. Escenario HABILITADA: reserva recursos, guarda horaSalidaCamion, evalúa clima favorable y confirma reserva con PATCH")
    void escenarioHabilitada() {
        OrdenTrabajoDTO ordenDto = crearOrdenDto(10L, 2L, 1L);
        OrdenTrabajo entidad = crearOrdenEntidad(10L, 2L, 1L);

        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA)).thenReturn(List.of(ordenDto));
        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA)).thenReturn(Collections.emptyList());
        when(ordenTrabajoRepository.findById(10L)).thenReturn(Optional.of(entidad));

        mockServer.expect(requestTo(obrasUrl + "/api/obras/internos/fases/2/contexto-ejecucion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "latitud": -31.245,
                          "longitud": -64.456,
                          "tipoTrabajo": "BASE",
                          "faseAnteriorCompletada": true
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(recursosUrl + "/api/recursos/internos/reservas"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id": 99,
                          "horaSalidaCamion": "2026-11-12T07:30:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        OrdenTrabajoDTO evaluadaDto = crearOrdenDto(10L, 2L, 1L);
        evaluadaDto.setEstado(EstadoOrden.HABILITADA);
        when(otService.evaluarYActualizarEstadoClimatico(10L, -31.245, -64.456, TipoTrabajo.BASE))
                .thenReturn(evaluadaDto);

        mockServer.expect(requestTo(recursosUrl + "/api/recursos/internos/reservas/99/confirmacion"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        job.ejecutarEvaluacionDiaria();

        mockServer.verify();
        assertEquals(99L, entidad.getReservaId());
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 30, 0), entidad.getHoraSalidaCamion());
        verify(otService).cambiarEstado(10L, EstadoOrden.RECURSOS_ASIGNADOS);
        verify(otService).evaluarYActualizarEstadoClimatico(10L, -31.245, -64.456, TipoTrabajo.BASE);
    }

    @Test
    @DisplayName("2. Escenario SUSPENDIDA: ejecuta compensación en orden (liberar reserva PATCH -> reprogramar fase -> crear orden nueva -> REPROGRAMADA)")
    void escenarioSuspendida() {
        OrdenTrabajoDTO ordenDto = crearOrdenDto(11L, 2L, 1L);
        OrdenTrabajo entidad = crearOrdenEntidad(11L, 2L, 1L);

        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA)).thenReturn(List.of(ordenDto));
        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA)).thenReturn(Collections.emptyList());
        when(ordenTrabajoRepository.findById(11L)).thenReturn(Optional.of(entidad));
        when(ordenTrabajoRepository.findByFaseId(2L)).thenReturn(List.of(entidad));

        mockServer.expect(requestTo(obrasUrl + "/api/obras/internos/fases/2/contexto-ejecucion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "latitud": -31.245,
                          "longitud": -64.456,
                          "tipoTrabajo": "BASE",
                          "faseAnteriorCompletada": true
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(recursosUrl + "/api/recursos/internos/reservas"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id": 105,
                          "horaSalidaCamion": "08:00:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        OrdenTrabajoDTO evaluadaDto = crearOrdenDto(11L, 2L, 1L);
        evaluadaDto.setEstado(EstadoOrden.SUSPENDIDA);
        when(otService.evaluarYActualizarEstadoClimatico(11L, -31.245, -64.456, TipoTrabajo.BASE))
                .thenReturn(evaluadaDto);

        mockServer.expect(requestTo(recursosUrl + "/api/recursos/internos/reservas/105/liberacion"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        mockServer.expect(requestTo(obrasUrl + "/api/obras/fases/2/programacion"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        OrdenTrabajoDTO hijaDto = crearOrdenDto(12L, 2L, 1L);
        hijaDto.setFechaProgramada(hoy.plusDays(1));
        when(otService.crearOrden(any(CrearOrdenRequest.class))).thenReturn(hijaDto);

        job.ejecutarEvaluacionDiaria();

        mockServer.verify();

        InOrder inOrder = inOrder(otService);
        inOrder.verify(otService).cambiarEstado(11L, EstadoOrden.RECURSOS_ASIGNADOS);
        inOrder.verify(otService).evaluarYActualizarEstadoClimatico(11L, -31.245, -64.456, TipoTrabajo.BASE);
        inOrder.verify(otService).crearOrden(any(CrearOrdenRequest.class));
        inOrder.verify(otService).cambiarEstado(11L, EstadoOrden.REPROGRAMADA);
    }

    @Test
    @DisplayName("3. Escenario 422 de Recursos -> Orden pasa a CANCELADA")
    void escenarioRecursos422PasaACancelada() {
        OrdenTrabajoDTO ordenDto = crearOrdenDto(13L, 3L, 2L);
        OrdenTrabajo entidad = crearOrdenEntidad(13L, 3L, 2L);

        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA)).thenReturn(List.of(ordenDto));
        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA)).thenReturn(Collections.emptyList());
        when(ordenTrabajoRepository.findById(13L)).thenReturn(Optional.of(entidad));

        mockServer.expect(requestTo(obrasUrl + "/api/obras/internos/fases/3/contexto-ejecucion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "latitud": -33.000,
                          "longitud": -65.000,
                          "tipoTrabajo": "CARPETA_ASFALTICA",
                          "faseAnteriorCompletada": true
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(recursosUrl + "/api/recursos/internos/reservas"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"mensaje\":\"Distancia excede límite\"}"));

        job.ejecutarEvaluacionDiaria();

        mockServer.verify();
        verify(otService).cambiarEstado(13L, EstadoOrden.CANCELADA);
        verify(otService, never()).evaluarYActualizarEstadoClimatico(any(), any(), any(), any());
    }

    @Test
    @DisplayName("4. Escenario Obras caído -> Orden queda PROGRAMADA")
    void escenarioObrasCaidoQuedaProgramada() {
        OrdenTrabajoDTO ordenDto = crearOrdenDto(14L, 2L, 1L);
        OrdenTrabajo entidad = crearOrdenEntidad(14L, 2L, 1L);

        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA)).thenReturn(List.of(ordenDto));
        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA)).thenReturn(Collections.emptyList());
        when(ordenTrabajoRepository.findById(14L)).thenReturn(Optional.of(entidad));

        mockServer.expect(requestTo(obrasUrl + "/api/obras/internos/fases/2/contexto-ejecucion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        job.ejecutarEvaluacionDiaria();

        mockServer.verify();
        verify(otService, never()).cambiarEstado(eq(14L), any());
        assertEquals(EstadoOrden.PROGRAMADA, entidad.getEstado());
    }

    @Test
    @DisplayName("5. Escenario fase anterior incompleta -> Orden queda PROGRAMADA")
    void escenarioFaseAnteriorIncompletaQuedaProgramada() {
        OrdenTrabajoDTO ordenDto = crearOrdenDto(15L, 2L, 1L);
        OrdenTrabajo entidad = crearOrdenEntidad(15L, 2L, 1L);

        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.PROGRAMADA)).thenReturn(List.of(ordenDto));
        when(otService.listarPorFechaYEstado(hoy, EstadoOrden.SUSPENDIDA)).thenReturn(Collections.emptyList());
        when(ordenTrabajoRepository.findById(15L)).thenReturn(Optional.of(entidad));

        mockServer.expect(requestTo(obrasUrl + "/api/obras/internos/fases/2/contexto-ejecucion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "latitud": -31.245,
                          "longitud": -64.456,
                          "tipoTrabajo": "BASE",
                          "faseAnteriorCompletada": false
                        }
                        """, MediaType.APPLICATION_JSON));

        job.ejecutarEvaluacionDiaria();

        mockServer.verify();
        verify(otService, never()).cambiarEstado(eq(15L), any());
        assertEquals(EstadoOrden.PROGRAMADA, entidad.getEstado());
    }
}
