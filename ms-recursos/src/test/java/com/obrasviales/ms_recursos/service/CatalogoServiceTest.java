package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CertificacionRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.MaquinaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.OperarioRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.PlantaRequest;
import com.obrasviales.ms_recursos.dto.ReservaResponse;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.entity.Reserva;
import com.obrasviales.ms_recursos.repository.CertificacionRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Catálogos de Recursos: datos únicos, valores por defecto y búsquedas que no encuentran nada.
@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private MaquinaRepository maquinaRepository;
    @Mock
    private OperarioRepository operarioRepository;
    @Mock
    private CertificacionRepository certificacionRepository;
    @Mock
    private PlantaMezclaRepository plantaRepository;
    @Mock
    private MantenimientoRepository mantenimientoRepository;
    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private CatalogoService service;

    private Maquina maquina(long id, String codigo) {
        Maquina m = new Maquina();
        m.setId(id);
        m.setCodigo(codigo);
        m.setTipo("PAVIMENTADORA");
        return m;
    }

    // ---------- Máquinas ----------

    @Test
    void crearMaquinaConCodigoRepetido_lanza409() {
        when(maquinaRepository.existsByCodigo("PAV-01")).thenReturn(true);

        assertThatThrownBy(() -> service.crearMaquina(new MaquinaRequest("PAV-01", "PAVIMENTADORA", null, null)))
                .isInstanceOf(ConflictoEstadoException.class);
        verify(maquinaRepository, never()).save(any());
    }

    @Test
    void crearMaquinaSinEstado_quedaDisponible() {
        when(maquinaRepository.existsByCodigo("EXC-01")).thenReturn(false);
        when(maquinaRepository.save(any(Maquina.class))).thenAnswer(inv -> inv.getArgument(0));

        MaquinaResponse r = service.crearMaquina(new MaquinaRequest("EXC-01", "EXCAVADORA", null, null));

        assertThat(r.estado()).isEqualTo("DISPONIBLE");
        assertThat(r.horasUso()).isZero();
        assertThat(r.cuadrillaId()).isNull();
    }

    @Test
    void actualizarMaquinaCambiaTipoYEstado_peroNoElCodigo() {
        Maquina m = maquina(3, "PAV-01");
        when(maquinaRepository.findById(3L)).thenReturn(Optional.of(m));
        when(maquinaRepository.save(any(Maquina.class))).thenAnswer(inv -> inv.getArgument(0));

        MaquinaResponse r = service.actualizarMaquina(3L, new MaquinaRequest("OTRO", "RODILLO", "EN_MANTENIMIENTO", null));

        assertThat(r.codigo()).isEqualTo("PAV-01");
        assertThat(r.tipo()).isEqualTo("RODILLO");
        assertThat(r.estado()).isEqualTo("EN_MANTENIMIENTO");
    }

    @Test
    void crearMaquinaConHorasDeUso_laGuardaConEsasHoras() {
        when(maquinaRepository.existsByCodigo("EXC-02")).thenReturn(false);
        when(maquinaRepository.save(any(Maquina.class))).thenAnswer(inv -> inv.getArgument(0));

        MaquinaResponse r = service.crearMaquina(new MaquinaRequest("EXC-02", "EXCAVADORA", null, 250));

        assertThat(r.horasUso()).isEqualTo(250);
    }

    @Test
    void actualizarMaquinaPuedeCorregirLasHorasDeUso() {
        Maquina m = maquina(3, "PAV-01");
        m.setHorasUso(10);
        when(maquinaRepository.findById(3L)).thenReturn(Optional.of(m));
        when(maquinaRepository.save(any(Maquina.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.actualizarMaquina(3L, new MaquinaRequest("PAV-01", "PAVIMENTADORA", null, 99)).horasUso())
                .isEqualTo(99);
        assertThat(service.actualizarMaquina(3L, new MaquinaRequest("PAV-01", "PAVIMENTADORA", null, null)).horasUso())
                .as("sin horasUso en el pedido se conserva el valor").isEqualTo(99);
    }

    @Test
    void actualizarMaquinaInexistente_lanza404() {
        when(maquinaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarMaquina(99L, new MaquinaRequest("X", "Y", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarMaquinasSinFecha_noConsultaMantenimientos() {
        when(maquinaRepository.findAll()).thenReturn(List.of(maquina(1, "A")));

        List<MaquinaResponse> lista = service.listarMaquinas(null);

        assertThat(lista).singleElement().satisfies(r -> assertThat(r.enMantenimiento()).isNull());
        verify(mantenimientoRepository, never()).tieneMantenimientoEnFecha(anyLong(), any());
    }

    @Test
    void listarMaquinasConFecha_informaSiTieneMantenimiento() {
        LocalDate fecha = LocalDate.of(2026, 12, 4);
        when(maquinaRepository.findAll()).thenReturn(List.of(maquina(1, "A"), maquina(2, "B")));
        when(mantenimientoRepository.tieneMantenimientoEnFecha(1L, fecha)).thenReturn(true);
        when(mantenimientoRepository.tieneMantenimientoEnFecha(2L, fecha)).thenReturn(false);

        List<MaquinaResponse> lista = service.listarMaquinas(fecha);

        assertThat(lista).extracting(MaquinaResponse::enMantenimiento).containsExactly(true, false);
    }

    // ---------- Operarios y certificaciones ----------

    @Test
    void crearOperarioConUsuarioRepetido_lanza409() {
        when(operarioRepository.existsByKeycloakUsername("capataz")).thenReturn(true);

        assertThatThrownBy(() -> service.crearOperario(new OperarioRequest("Otro", "capataz", "PEON")))
                .isInstanceOf(ConflictoEstadoException.class);
    }

    @Test
    void crearOperarioSinRol_quedaComoPeonYActivoSinCuadrilla() {
        when(operarioRepository.save(any(Operario.class))).thenAnswer(inv -> inv.getArgument(0));

        var r = service.crearOperario(new OperarioRequest("Ana", null, null));

        assertThat(r.rolOperativo()).isEqualTo("PEON");
        assertThat(r.estado()).isEqualTo("ACTIVO");
        assertThat(r.cuadrillaId()).isNull();
        assertThat(r.certificaciones()).isEmpty();
    }

    @Test
    void agregarCertificacionAOperarioInexistente_lanza404() {
        when(operarioRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agregarCertificacion(5L,
                new CertificacionRequest("PAVIMENTADORA", LocalDate.now().plusYears(1))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void agregarCertificacionGuardaTipoYVencimiento() {
        Operario o = new Operario();
        o.setId(5L);
        when(operarioRepository.findById(5L)).thenReturn(Optional.of(o));
        when(certificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDate vence = LocalDate.now().plusYears(1);

        var r = service.agregarCertificacion(5L, new CertificacionRequest("RODILLO_COMPACTADOR", vence));

        assertThat(r.tipoMaquina()).isEqualTo("RODILLO_COMPACTADOR");
        assertThat(r.vencimiento()).isEqualTo(vence);
    }

    // ---------- Asignaciones del operario (F-15) ----------

    @Test
    void asignacionesDeUnUsuarioSinOperario_lanza404() {
        when(operarioRepository.findByKeycloakUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.asignacionesDe("fantasma", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void asignacionesDeUnOperarioSinCuadrilla_esListaVacia() {
        Operario o = new Operario();
        when(operarioRepository.findByKeycloakUsername("operario")).thenReturn(Optional.of(o));

        assertThat(service.asignacionesDe("operario", null)).isEmpty();
        verify(reservaRepository, never()).asignacionesVigentes(anyLong(), any(), any());
    }

    @Test
    void asignacionesDeUnOperario_sonLasReservasVigentesDeSuCuadrilla() {
        Cuadrilla c = new Cuadrilla();
        c.setId(1L);
        Operario o = new Operario();
        o.setCuadrilla(c);
        Reserva r = new Reserva();
        r.setId(10L);
        r.setOrdenTrabajoId(77L);
        r.setCuadrilla(c);
        r.setEstado("CONFIRMADA");
        when(operarioRepository.findByKeycloakUsername("operario")).thenReturn(Optional.of(o));
        when(reservaRepository.asignacionesVigentes(anyLong(), any(), any())).thenReturn(List.of(r));

        List<ReservaResponse> lista = service.asignacionesDe("operario", null);

        assertThat(lista).singleElement().satisfies(x -> {
            assertThat(x.ordenTrabajoId()).isEqualTo(77L);
            assertThat(x.cuadrillaId()).isEqualTo(1L);
        });
    }

    @Test
    void asignacionesConFecha_dejanSoloLasDeEseDia() {
        Cuadrilla c = new Cuadrilla();
        c.setId(1L);
        Operario o = new Operario();
        o.setCuadrilla(c);
        Reserva hoy = new Reserva();
        hoy.setId(1L);
        hoy.setCuadrilla(c);
        hoy.setFechaReserva(LocalDate.of(2026, 12, 4));
        Reserva otroDia = new Reserva();
        otroDia.setId(2L);
        otroDia.setCuadrilla(c);
        otroDia.setFechaReserva(LocalDate.of(2026, 12, 5));
        when(operarioRepository.findByKeycloakUsername("operario")).thenReturn(Optional.of(o));
        when(reservaRepository.asignacionesVigentes(anyLong(), any(), any())).thenReturn(List.of(hoy, otroDia));

        assertThat(service.asignacionesDe("operario", LocalDate.of(2026, 12, 4)))
                .extracting(ReservaResponse::id).containsExactly(1L);
        assertThat(service.asignacionesDe("operario", null)).hasSize(2);
    }

    // ---------- Plantas ----------

    @Test
    void crearPlantaSinIndicarActiva_quedaActiva() {
        when(plantaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = service.crearPlanta(new PlantaRequest("Planta Sur", -31.4, -64.2, null));

        assertThat(r.activa()).isTrue();
    }

    @Test
    void crearPlantaInactiva_respetaElValor() {
        when(plantaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = service.crearPlanta(new PlantaRequest("Planta Norte", -31.4, -64.2, false));

        assertThat(r.activa()).isFalse();
    }
}
