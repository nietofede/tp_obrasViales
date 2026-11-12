package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_recursos.client.ProveedorRutas;
import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import com.obrasviales.ms_recursos.entity.ConfiguracionDesplazamiento;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Reglas de elegibilidad de "proponer y reservar" (RN-04 a RN-08): casos normales, de límite y de rechazo.
// Un rechazo es una ReglaNegocioException (422) con el código de la regla y el motivo de cada cuadrilla.
@ExtendWith(MockitoExtension.class)
class ReservaServiceReglasTest {

    private static final Long ORDEN = 101L;
    private static final LocalDate FECHA = LocalDate.of(2026, 12, 4);
    private static final double TRAMO_LAT = -31.42;
    private static final double TRAMO_LON = -64.18;
    // Latitudes de origen distintas, para poder devolver una ruta a las cuadrillas y otra a la planta.
    private static final double CUADRILLA_LAT = -31.38;
    private static final double PLANTA_LAT = -31.35;

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

    // ---------- RN-05: sin solapamiento ----------

    @Test
    void rn05_cuadrillaConReservaVigente_esRechazada() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(reservaRepository.existeReservaSolapada(eq(1L), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(true);

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        assertEquals("RN-05", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("Alfa"));
    }

    @Test
    void rn05_conVariasCuadrillas_pasaALaSiguienteLibre() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa"), cuadrilla(2, "Beta")));
        when(reservaRepository.existeReservaSolapada(eq(1L), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(true);
        when(reservaRepository.existeReservaSolapada(eq(2L), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(operarioRepository.findByCuadrillaIdAndEstado(2L, "ACTIVO")).thenReturn(operarios(1, 2));
        stubRuta(CUADRILLA_LAT, 14);
        guardar();

        Reserva reserva = proponer("RELEVAMIENTO");

        assertEquals(2L, reserva.getCuadrilla().getId());
    }

    // ---------- RN-06: composición mínima de la cuadrilla ----------

    @Test
    void rn06_pocosOperarios_esRechazada() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1));

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        assertEquals("RN-06", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("1 operarios activos y se necesitan 2"));
    }

    @Test
    void rn06_faltanMaquinasDelTipoRequerido_esRechazada() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("BASE", 2, "RODILLO_COMPACTADOR", 1, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1, 2));
        when(maquinaRepository.findByCuadrillaIdAndEstado(1L, "DISPONIBLE")).thenReturn(List.of());

        ReglaNegocioException ex = rechazo("BASE");

        assertEquals("RN-06", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("RODILLO_COMPACTADOR"));
    }

    @Test
    void rn06_maquinaEnMantenimientoEseDia_noCuenta() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("BASE", 2, "RODILLO_COMPACTADOR", 1, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1, 2));
        when(maquinaRepository.findByCuadrillaIdAndEstado(1L, "DISPONIBLE"))
                .thenReturn(List.of(maquina(5, "RODILLO_COMPACTADOR")));
        when(mantenimientoRepository.tieneMantenimientoEnFecha(5L, FECHA)).thenReturn(true);

        assertEquals("RN-06", rechazo("BASE").getCodigoRegla());
    }

    // ---------- RN-04: certificación vigente ----------

    @Test
    void rn04_sinCertificacionVigente_esRechazada() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("BASE", 2, "RODILLO_COMPACTADOR", 1, false));
        stubCuadrillaConMaquina("RODILLO_COMPACTADOR");
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                anyLong(), eq("RODILLO_COMPACTADOR"), eq(FECHA))).thenReturn(false);

        ReglaNegocioException ex = rechazo("BASE");

        assertEquals("RN-04", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("certificación vigente"));
    }

    @Test
    void rn04_conCertificacionVigente_esElegible() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("BASE", 2, "RODILLO_COMPACTADOR", 1, false));
        stubCuadrillaConMaquina("RODILLO_COMPACTADOR");
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                anyLong(), eq("RODILLO_COMPACTADOR"), eq(FECHA))).thenReturn(true);
        stubRuta(CUADRILLA_LAT, 14);
        guardar();

        Reserva reserva = proponer("BASE");

        assertEquals("PROVISORIA", reserva.getEstado());
        assertEquals(1L, reserva.getCuadrilla().getId());
    }

    // ---------- RN-07: viaje de la cuadrilla al tramo ----------

    @Test
    void rn07_viajeQueSuperaElMaximo_esRechazado() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        stubCuadrillaSinMaquina();
        stubRuta(CUADRILLA_LAT, 91);

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        assertEquals("RN-07", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("91 min y el máximo es 90 min"));
    }

    @Test
    void rn07_viajeIgualAlMaximo_esElegible() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        stubCuadrillaSinMaquina();
        stubRuta(CUADRILLA_LAT, 90);
        guardar();

        assertEquals(90, proponer("RELEVAMIENTO").getTiempoViajeCuadrillaMin());
    }

    @Test
    void rn07_idaYVueltaQueNoEntraEnLaJornada_esRechazada() {
        // Máximo de viaje alto para que solo importe la jornada: 240 min de ida + 240 de vuelta = 480, no menos de 480.
        stubConfigYComposicion(config(300, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        stubCuadrillaSinMaquina();
        stubRuta(CUADRILLA_LAT, 240);

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        assertEquals("RN-07", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("ida y vuelta"));
    }

    @Test
    void rn07_idaYVueltaJustoDentroDeLaJornada_esElegible() {
        stubConfigYComposicion(config(300, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        stubCuadrillaSinMaquina();
        stubRuta(CUADRILLA_LAT, 239);
        guardar();

        assertEquals(239, proponer("RELEVAMIENTO").getTiempoViajeCuadrillaMin());
    }

    // ---------- RN-08: ventana de transporte de la mezcla y salida del camión ----------

    @Test
    void rn08_plantaFueraDeLaVentana_esRechazada() {
        stubTrabajoConMezcla(config(90, 480, 60, 15));
        stubRuta(CUADRILLA_LAT, 14);
        when(plantaRepository.findByActivaTrue()).thenReturn(List.of(planta(3, "Planta Norte")));
        stubRuta(PLANTA_LAT, 61);

        ReglaNegocioException ex = rechazo("CARPETA_ASFALTICA");

        assertEquals("RN-08", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("Planta Norte: 61 min"));
    }

    @Test
    void rn08_plantaEnElLimiteDeLaVentana_calculaLaSalidaDelCamion() {
        stubTrabajoConMezcla(config(90, 480, 60, 15));
        stubRuta(CUADRILLA_LAT, 14);
        when(plantaRepository.findByActivaTrue()).thenReturn(List.of(planta(3, "Planta Norte")));
        stubRuta(PLANTA_LAT, 60);
        guardar();

        Reserva reserva = proponer("CARPETA_ASFALTICA");

        // La cuadrilla llega a las 08:00 + 14 min = 08:14; el camión sale 60 min de viaje + 15 de margen antes: 06:59.
        assertEquals(LocalTime.of(6, 59), reserva.getHoraSalidaCamion());
        assertEquals(60, reserva.getTiempoViajeMezclaMin());
        assertEquals(3L, reserva.getPlanta().getId());
    }

    @Test
    void rn08_horaDeSalidaDelCamion_conLosValoresDeLaDemo() {
        stubTrabajoConMezcla(config(90, 480, 60, 15));
        stubRuta(CUADRILLA_LAT, 14);
        when(plantaRepository.findByActivaTrue()).thenReturn(List.of(planta(3, "Planta Norte")));
        stubRuta(PLANTA_LAT, 20);
        guardar();

        // 08:14 menos (20 de viaje + 15 de margen) = 07:39
        assertEquals(LocalTime.of(7, 39), proponer("CARPETA_ASFALTICA").getHoraSalidaCamion());
    }

    // ---------- Motivo del rechazo ----------

    @Test
    void rechazo_informaLaReglaDeLaCuadrillaQueLlegoMasLejos() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa"), cuadrilla(2, "Beta")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1, 2));
        when(operarioRepository.findByCuadrillaIdAndEstado(2L, "ACTIVO")).thenReturn(List.of());
        stubRuta(CUADRILLA_LAT, 600);

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        // Alfa falla en RN-07 (viaje) y Beta en RN-06 (operarios): se informa RN-07, la más avanzada, y ambos motivos.
        assertEquals("RN-07", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("Alfa") && ex.getMessage().contains("Beta"));
    }

    @Test
    void tipoDeTrabajoNoConfigurado_esRechazado() {
        when(configRepository.findById(1L)).thenReturn(Optional.of(config(90, 480, 60, 15)));
        when(composicionMinimaRepository.findById("PAVIMENTACION")).thenReturn(Optional.empty());

        ReglaNegocioException ex = rechazo("PAVIMENTACION");

        assertEquals("RN-06", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("PAVIMENTACION"));
    }

    @Test
    void sinCuadrillasCargadas_esRechazado() {
        stubConfigYComposicion(config(90, 480, 60, 15), composicion("RELEVAMIENTO", 2, null, 0, false));
        when(cuadrillaRepository.findAll()).thenReturn(List.of());

        ReglaNegocioException ex = rechazo("RELEVAMIENTO");

        assertTrue(ex.getMessage().contains("no hay cuadrillas cargadas"));
    }

    // ---------- Apoyo ----------

    private Reserva proponer(String tipo) {
        return servicio.proponerYReservar(ORDEN, tipo, FECHA, TRAMO_LAT, TRAMO_LON);
    }

    private ReglaNegocioException rechazo(String tipo) {
        return assertThrows(ReglaNegocioException.class, () -> proponer(tipo));
    }

    private ConfiguracionDesplazamiento config(int viajeMaximo, int jornada, int ventanaMezcla, int margenCamion) {
        ConfiguracionDesplazamiento c = new ConfiguracionDesplazamiento();
        c.setId(1L);
        c.setViajeMaximoCuadrillaMin(viajeMaximo);
        c.setDuracionJornadaMin(jornada);
        c.setVentanaTransporteMezclaMin(ventanaMezcla);
        c.setMargenCamionMin(margenCamion);
        return c;
    }

    private ComposicionMinimaTrabajo composicion(String tipo, int minOperarios, String maquina, int cantidad,
                                                 boolean mezcla) {
        ComposicionMinimaTrabajo c = new ComposicionMinimaTrabajo();
        c.setTipoTrabajo(tipo);
        c.setMinOperarios(minOperarios);
        c.setTipoMaquinaRequerida(maquina);
        c.setCantidadMaquinas(cantidad);
        c.setRequiereMezcla(mezcla);
        return c;
    }

    private Cuadrilla cuadrilla(long id, String nombre) {
        Cuadrilla c = new Cuadrilla();
        c.setId(id);
        c.setNombre(nombre);
        c.setUltimaLatitud(CUADRILLA_LAT);
        c.setUltimaLongitud(-64.22);
        return c;
    }

    private List<Operario> operarios(long... ids) {
        List<Operario> lista = new ArrayList<>();
        for (long id : ids) {
            Operario o = new Operario();
            o.setId(id);
            lista.add(o);
        }
        return lista;
    }

    private Maquina maquina(long id, String tipo) {
        Maquina m = new Maquina();
        m.setId(id);
        m.setTipo(tipo);
        return m;
    }

    private PlantaMezcla planta(long id, String nombre) {
        PlantaMezcla p = new PlantaMezcla();
        p.setId(id);
        p.setNombre(nombre);
        p.setLatitud(PLANTA_LAT);
        p.setLongitud(-64.20);
        return p;
    }

    private void stubConfigYComposicion(ConfiguracionDesplazamiento config, ComposicionMinimaTrabajo composicion) {
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        when(composicionMinimaRepository.findById(composicion.getTipoTrabajo())).thenReturn(Optional.of(composicion));
    }

    // Una cuadrilla libre con 2 operarios y sin máquina requerida (trabajo tipo RELEVAMIENTO).
    private void stubCuadrillaSinMaquina() {
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1, 2));
    }

    // Una cuadrilla libre con 2 operarios y una máquina disponible, sin mantenimiento, del tipo indicado.
    private void stubCuadrillaConMaquina(String tipoMaquina) {
        stubCuadrillaSinMaquina();
        when(maquinaRepository.findByCuadrillaIdAndEstado(1L, "DISPONIBLE"))
                .thenReturn(List.of(maquina(5, tipoMaquina)));
        when(mantenimientoRepository.tieneMantenimientoEnFecha(5L, FECHA)).thenReturn(false);
    }

    // Trabajo de CARPETA_ASFALTICA: 3 operarios, una PAVIMENTADORA certificada y mezcla (queda por stubbear la ruta).
    private void stubTrabajoConMezcla(ConfiguracionDesplazamiento config) {
        stubConfigYComposicion(config, composicion("CARPETA_ASFALTICA", 3, "PAVIMENTADORA", 1, true));
        when(cuadrillaRepository.findAll()).thenReturn(List.of(cuadrilla(1, "Alfa")));
        when(operarioRepository.findByCuadrillaIdAndEstado(1L, "ACTIVO")).thenReturn(operarios(1, 2, 3));
        when(maquinaRepository.findByCuadrillaIdAndEstado(1L, "DISPONIBLE"))
                .thenReturn(List.of(maquina(5, "PAVIMENTADORA")));
        when(mantenimientoRepository.tieneMantenimientoEnFecha(5L, FECHA)).thenReturn(false);
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                anyLong(), eq("PAVIMENTADORA"), eq(FECHA))).thenReturn(true);
    }

    private void stubRuta(double latitudOrigen, int minutos) {
        when(proveedorRutas.calcularRuta(eq(latitudOrigen), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new ProveedorRutas.ResultadoRuta(minutos, 10.0, "OSRM"));
    }

    private void guardar() {
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
    }
}
