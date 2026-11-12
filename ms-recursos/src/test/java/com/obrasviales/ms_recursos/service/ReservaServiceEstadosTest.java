package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.client.ProveedorRutas;
import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import com.obrasviales.ms_recursos.entity.ConfiguracionDesplazamiento;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.entity.PlantaMezcla;
import com.obrasviales.ms_recursos.entity.Reserva;
import com.obrasviales.ms_recursos.repository.CertificacionRepository;
import com.obrasviales.ms_recursos.repository.ComposicionMinimaRepository;
import com.obrasviales.ms_recursos.repository.ConfiguracionDesplazamientoRepository;
import com.obrasviales.ms_recursos.repository.CuadrillaRepository;
import com.obrasviales.ms_recursos.repository.MantenimientoRepository;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import com.obrasviales.ms_recursos.repository.PlantaMezclaRepository;
import com.obrasviales.ms_recursos.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Estados de una reserva: PROVISORIA -> CONFIRMADA | LIBERADA | EXPIRADA; CONFIRMADA -> LIBERADA.
// Confirmar y liberar son idempotentes (Operaciones puede reintentar) y proponer solo reutiliza
// la reserva de una orden si todavia bloquea recursos.
@ExtendWith(MockitoExtension.class)
class ReservaServiceEstadosTest {

    private static final Long ID = 7L;
    private static final Long ORDEN = 101L;
    private static final LocalDate FECHA = LocalDate.of(2026, 12, 4);

    @Mock private ReservaRepository reservaRepository;
    @Mock private CuadrillaRepository cuadrillaRepository;
    @Mock private OperarioRepository operarioRepository;
    @Mock private MaquinaRepository maquinaRepository;
    @Mock private CertificacionRepository certificacionRepository;
    @Mock private ComposicionMinimaRepository composicionMinimaRepository;
    @Mock private ConfiguracionDesplazamientoRepository configRepository;
    @Mock private PlantaMezclaRepository plantaRepository;
    @Mock private MantenimientoRepository mantenimientoRepository;
    @Mock private ProveedorRutas proveedorRutas;

    @InjectMocks
    private ReservaService servicio;

    private Reserva reserva(String estado, LocalDateTime vencimiento) {
        Reserva r = new Reserva();
        r.setId(ID);
        r.setOrdenTrabajoId(ORDEN);
        r.setEstado(estado);
        r.setFechaReserva(FECHA);
        r.setFechaVencimientoTtl(vencimiento);
        return r;
    }

    private Reserva vigente(String estado) {
        return reserva(estado, LocalDateTime.now().plusMinutes(20));
    }

    private void guardarDevuelveLoGuardado() {
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- Confirmar ----------

    @Test
    void confirmar_provisoriaVigente_pasaAConfirmada() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(vigente("PROVISORIA")));
        guardarDevuelveLoGuardado();

        assertEquals("CONFIRMADA", servicio.confirmarReserva(ID).getEstado());
    }

    @Test
    void confirmar_yaConfirmada_esIdempotenteYNoGuarda() {
        Reserva confirmada = vigente("CONFIRMADA");
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(confirmada));

        assertSame(confirmada, servicio.confirmarReserva(ID));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void confirmar_liberada_lanzaConflictoYNoCambiaElEstado() {
        Reserva liberada = vigente("LIBERADA");
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(liberada));

        assertThrows(ConflictoEstadoException.class, () -> servicio.confirmarReserva(ID));
        assertEquals("LIBERADA", liberada.getEstado());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void confirmar_expirada_lanzaConflicto() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(vigente("EXPIRADA")));

        assertThrows(ConflictoEstadoException.class, () -> servicio.confirmarReserva(ID));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void confirmar_provisoriaVencida_laGuardaExpiradaYLanzaConflicto() {
        Reserva vencida = reserva("PROVISORIA", LocalDateTime.now().minusMinutes(5));
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(vencida));

        assertThrows(ConflictoEstadoException.class, () -> servicio.confirmarReserva(ID));
        assertEquals("EXPIRADA", vencida.getEstado());
        verify(reservaRepository).save(vencida);
    }

    @Test
    void confirmar_inexistente_lanzaNoEncontrada() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.confirmarReserva(ID));
    }

        @Test
    void confirmar_yaConfirmadaConVencimientoViejo_sigueConfirmadaYNoGuarda() {
        // Una reserva confirmada conserva el vencimiento de cuando era provisoria: pasados los 30 minutos
        // ese vencimiento ya no significa nada y no debe volver a marcarla EXPIRADA.
        Reserva confirmada = reserva("CONFIRMADA", LocalDateTime.now().minusHours(2));
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(confirmada));

        assertSame(confirmada, servicio.confirmarReserva(ID));
        assertEquals("CONFIRMADA", confirmada.getEstado());
        verify(reservaRepository, never()).save(any());
    }

    // ---------- Liberar ----------

    @Test
    void liberar_provisoria_pasaALiberada() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(vigente("PROVISORIA")));
        guardarDevuelveLoGuardado();

        assertEquals("LIBERADA", servicio.liberarReserva(ID).getEstado());
    }

    @Test
    void liberar_confirmada_pasaALiberada() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(vigente("CONFIRMADA")));
        guardarDevuelveLoGuardado();

        assertEquals("LIBERADA", servicio.liberarReserva(ID).getEstado());
    }

    @Test
    void liberar_yaLiberada_esIdempotenteYNoGuarda() {
        Reserva liberada = vigente("LIBERADA");
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(liberada));

        assertSame(liberada, servicio.liberarReserva(ID));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void liberar_expirada_quedaExpiradaYNoGuarda() {
        Reserva expirada = vigente("EXPIRADA");
        when(reservaRepository.findById(ID)).thenReturn(Optional.of(expirada));

        assertEquals("EXPIRADA", servicio.liberarReserva(ID).getEstado());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void liberar_inexistente_lanzaNoEncontrada() {
        when(reservaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.liberarReserva(ID));
    }

    // ---------- Proponer y reservar sobre una orden que ya tiene reserva ----------

    @Test
    void proponer_reservaProvisoriaVigente_laDevuelveSinReevaluar() {
        Reserva existente = vigente("PROVISORIA");
        when(reservaRepository.findByOrdenTrabajoId(ORDEN)).thenReturn(Optional.of(existente));

        assertSame(existente, servicio.proponerYReservar(ORDEN, "RELEVAMIENTO", FECHA, -31.42, -64.18));
        verifyNoInteractions(configRepository, cuadrillaRepository, proveedorRutas);
    }

    @Test
    void proponer_reservaConfirmada_laDevuelveSinReevaluar() {
        Reserva existente = vigente("CONFIRMADA");
        when(reservaRepository.findByOrdenTrabajoId(ORDEN)).thenReturn(Optional.of(existente));

        assertSame(existente, servicio.proponerYReservar(ORDEN, "RELEVAMIENTO", FECHA, -31.42, -64.18));
        verifyNoInteractions(configRepository, cuadrillaRepository, proveedorRutas);
    }

    @Test
    void proponer_reservaLiberada_reevaluaYReutilizaLaFila() {
        Reserva existente = reservaConDatosViejosDeMezcla("LIBERADA", LocalDateTime.now().plusMinutes(20));
        stubEvaluacionExitosa(existente);

        Reserva resultado = servicio.proponerYReservar(ORDEN, "RELEVAMIENTO", FECHA, -31.42, -64.18);

        assertReevaluada(existente, resultado);
    }

    @Test
    void proponer_reservaExpirada_reevaluaYReutilizaLaFila() {
        Reserva existente = reservaConDatosViejosDeMezcla("EXPIRADA", LocalDateTime.now().minusHours(1));
        stubEvaluacionExitosa(existente);

        Reserva resultado = servicio.proponerYReservar(ORDEN, "RELEVAMIENTO", FECHA, -31.42, -64.18);

        assertReevaluada(existente, resultado);
    }

    @Test
    void proponer_provisoriaVencida_reevaluaYReutilizaLaFila() {
        Reserva existente = reservaConDatosViejosDeMezcla("PROVISORIA", LocalDateTime.now().minusMinutes(5));
        stubEvaluacionExitosa(existente);

        Reserva resultado = servicio.proponerYReservar(ORDEN, "RELEVAMIENTO", FECHA, -31.42, -64.18);

        assertReevaluada(existente, resultado);
    }

    // ---------- Apoyo ----------

    private Reserva reservaConDatosViejosDeMezcla(String estado, LocalDateTime vencimiento) {
        Reserva r = reserva(estado, vencimiento);
        r.setPlanta(new PlantaMezcla());
        r.setTiempoViajeMezclaMin(20);
        r.setHoraSalidaCamion(LocalTime.of(7, 39));
        return r;
    }

    // Evaluacion de un trabajo sin maquina ni mezcla (RELEVAMIENTO): una cuadrilla libre, con 2 operarios y 14 min de viaje.
    private void stubEvaluacionExitosa(Reserva existente) {
        when(reservaRepository.findByOrdenTrabajoId(ORDEN)).thenReturn(Optional.of(existente));

        ConfiguracionDesplazamiento config = new ConfiguracionDesplazamiento();
        config.setId(1L);
        config.setViajeMaximoCuadrillaMin(90);
        config.setDuracionJornadaMin(480);
        config.setVentanaTransporteMezclaMin(60);
        config.setMargenCamionMin(15);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));

        ComposicionMinimaTrabajo composicion = new ComposicionMinimaTrabajo();
        composicion.setTipoTrabajo("RELEVAMIENTO");
        composicion.setMinOperarios(2);
        composicion.setTipoMaquinaRequerida(null);
        composicion.setCantidadMaquinas(0);
        composicion.setRequiereMezcla(false);
        when(composicionMinimaRepository.findById("RELEVAMIENTO")).thenReturn(Optional.of(composicion));

        Cuadrilla cuadrilla = new Cuadrilla();
        cuadrilla.setId(1L);
        cuadrilla.setUltimaLatitud(-31.38);
        cuadrilla.setUltimaLongitud(-64.22);
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla));
        when(reservaRepository.existeReservaSolapada(eq(1L), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(false);

        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(new Operario(), new Operario()));
        when(proveedorRutas.calcularRuta(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new ProveedorRutas.ResultadoRuta(14, 10.0, "OSRM"));

        guardarDevuelveLoGuardado();
    }

    private void assertReevaluada(Reserva existente, Reserva resultado) {
        assertSame(existente, resultado);
        assertEquals("PROVISORIA", resultado.getEstado());
        assertTrue(resultado.getFechaVencimientoTtl().isAfter(LocalDateTime.now()));
        assertNull(resultado.getPlanta());
        assertNull(resultado.getTiempoViajeMezclaMin());
        assertNull(resultado.getHoraSalidaCamion());
    }
}