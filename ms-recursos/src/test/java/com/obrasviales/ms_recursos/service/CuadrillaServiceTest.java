package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaRequest;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.UbicacionRequest;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.repository.CertificacionRepository;
import com.obrasviales.ms_recursos.repository.CuadrillaRepository;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Armado de cuadrillas: RN-04 (certificación vigente para cada tipo de máquina), pertenencia a una sola cuadrilla
// y check-in del capataz.
@ExtendWith(MockitoExtension.class)
class CuadrillaServiceTest {

    @Mock
    private CuadrillaRepository cuadrillaRepository;
    @Mock
    private OperarioRepository operarioRepository;
    @Mock
    private MaquinaRepository maquinaRepository;
    @Mock
    private CertificacionRepository certificacionRepository;

    @InjectMocks
    private CuadrillaService service;

    @BeforeEach
    void guardadoQueDevuelveLoMismo() {
        lenient().when(cuadrillaRepository.save(any(Cuadrilla.class))).thenAnswer(inv -> {
            Cuadrilla c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(10L);
            }
            return c;
        });
        lenient().when(operarioRepository.findByCuadrillaId(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(maquinaRepository.findByCuadrillaId(anyLong())).thenReturn(new ArrayList<>());
    }

    private Operario operario(long id, String estado, Cuadrilla cuadrilla) {
        Operario o = new Operario();
        o.setId(id);
        o.setNombre("Operario " + id);
        o.setEstado(estado);
        o.setCuadrilla(cuadrilla);
        return o;
    }

    private Maquina maquina(long id, String tipo, String estado, Cuadrilla cuadrilla) {
        Maquina m = new Maquina();
        m.setId(id);
        m.setCodigo("M-" + id);
        m.setTipo(tipo);
        m.setEstado(estado);
        m.setCuadrilla(cuadrilla);
        return m;
    }

    private Cuadrilla cuadrilla(long id) {
        Cuadrilla c = new Cuadrilla();
        c.setId(id);
        c.setNombre("Cuadrilla " + id);
        return c;
    }

    private CuadrillaRequest request(List<Long> operarios, List<Long> maquinas) {
        return new CuadrillaRequest("Gamma", -31.4, -64.2, operarios, maquinas);
    }

    // ---------- Alta ----------

    @Test
    void nombreRepetido_lanza409() {
        when(cuadrillaRepository.existsByNombre("Gamma")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of())))
                .isInstanceOf(ConflictoEstadoException.class);
        verify(cuadrillaRepository, never()).save(any());
    }

    @Test
    void sinOperarios_lanza422Rn06() {
        assertThatThrownBy(() -> service.crear(request(List.of(), List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigoRegla()).isEqualTo("RN-06"));
    }

    @Test
    void operarioInexistente_lanza404() {
        when(operarioRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(request(List.of(9L), List.of())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void operarioInactivo_lanza409() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario(1, "INACTIVO", null)));

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of())))
                .isInstanceOf(ConflictoEstadoException.class);
    }

    @Test
    void operarioDeOtraCuadrilla_lanza409() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario(1, "ACTIVO", cuadrilla(2))));

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of())))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("otra cuadrilla");
    }

    @Test
    void maquinaDeOtraCuadrilla_lanza409() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario(1, "ACTIVO", null)));
        when(maquinaRepository.findById(5L))
                .thenReturn(Optional.of(maquina(5, "PAVIMENTADORA", "DISPONIBLE", cuadrilla(2))));

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of(5L))))
                .isInstanceOf(ConflictoEstadoException.class);
    }

    @Test
    void maquinaFueraDeServicio_lanza409() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario(1, "ACTIVO", null)));
        when(maquinaRepository.findById(5L))
                .thenReturn(Optional.of(maquina(5, "PAVIMENTADORA", "FUERA_DE_SERVICIO", null)));

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of(5L))))
                .isInstanceOf(ConflictoEstadoException.class);
    }

    @Test
    void rn04_sinNingunCertificadoVigenteParaElTipoDeMaquina_lanza422() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario(1, "ACTIVO", null)));
        when(maquinaRepository.findById(5L))
                .thenReturn(Optional.of(maquina(5, "PAVIMENTADORA", "DISPONIBLE", null)));
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                eq(1L), eq("PAVIMENTADORA"), any(LocalDate.class))).thenReturn(false);

        assertThatThrownBy(() -> service.crear(request(List.of(1L), List.of(5L))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCodigoRegla()).isEqualTo("RN-04");
                    assertThat(e.getMessage()).contains("PAVIMENTADORA");
                });
    }

    @Test
    void rn04_bastaUnOperarioCertificadoEntreVarios() {
        Operario sinCertificado = operario(1, "ACTIVO", null);
        Operario certificado = operario(2, "ACTIVO", null);
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(sinCertificado));
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(certificado));
        when(maquinaRepository.findById(5L))
                .thenReturn(Optional.of(maquina(5, "PAVIMENTADORA", "DISPONIBLE", null)));
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                eq(1L), anyString(), any(LocalDate.class))).thenReturn(false);
        when(certificacionRepository.existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(
                eq(2L), eq("PAVIMENTADORA"), any(LocalDate.class))).thenReturn(true);

        CuadrillaResponse r = service.crear(request(List.of(1L, 2L), List.of(5L)));

        assertThat(r.id()).isEqualTo(10L);
        assertThat(sinCertificado.getCuadrilla()).isNotNull();
        assertThat(certificado.getCuadrilla()).isNotNull();
    }

    @Test
    void cuadrillaSinMaquinas_noExigeCertificaciones() {
        Operario o = operario(1, "ACTIVO", null);
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(o));

        service.crear(request(List.of(1L), List.of()));

        assertThat(o.getCuadrilla().getId()).isEqualTo(10L);
        verify(certificacionRepository, never())
                .existsByOperarioIdAndTipoMaquinaAndVencimientoGreaterThanEqual(anyLong(), anyString(), any());
    }

    @Test
    void idsRepetidosEnLaLista_seTomanUnaSolaVez() {
        Operario o = operario(1, "ACTIVO", null);
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(o));

        service.crear(request(List.of(1L, 1L), List.of()));

        verify(operarioRepository).findById(1L);
    }

    // ---------- Modificación ----------

    @Test
    void actualizarLiberaAQuienesYaNoFiguran() {
        Cuadrilla c = cuadrilla(3);
        Operario sigue = operario(1, "ACTIVO", c);
        Operario sale = operario(2, "ACTIVO", c);
        when(cuadrillaRepository.findById(3L)).thenReturn(Optional.of(c));
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(sigue));
        when(operarioRepository.findByCuadrillaId(3L)).thenReturn(List.of(sigue, sale));

        service.actualizar(3L, request(List.of(1L), List.of()));

        assertThat(sale.getCuadrilla()).isNull();
        assertThat(sigue.getCuadrilla()).isSameAs(c);
        verify(operarioRepository).save(sale);
    }

    @Test
    void actualizarConNombreDeOtraCuadrilla_lanza409() {
        when(cuadrillaRepository.findById(3L)).thenReturn(Optional.of(cuadrilla(3)));
        when(cuadrillaRepository.existsByNombreAndIdNot("Gamma", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(3L, request(List.of(1L), List.of())))
                .isInstanceOf(ConflictoEstadoException.class);
    }

    @Test
    void actualizarCuadrillaInexistente_lanza404() {
        when(cuadrillaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(99L, request(List.of(1L), List.of())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- Check-in ----------

    @Test
    void checkInDelCapatazDeLaCuadrilla_actualizaLaUbicacion() {
        Cuadrilla c = cuadrilla(1);
        Operario capataz = operario(2, "ACTIVO", c);
        capataz.setRolOperativo("CAPATAZ");
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(operarioRepository.findByKeycloakUsername("capataz")).thenReturn(Optional.of(capataz));

        CuadrillaResponse r = service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "capataz");

        assertThat(r.ultimaLatitud()).isEqualTo(-32.0);
        assertThat(r.ultimaLongitud()).isEqualTo(-65.0);
        assertThat(c.getFechaCheckin()).isNotNull();
    }

    @Test
    void checkInDeUnCapatazDeOtraCuadrilla_esAccesoDenegado() {
        Cuadrilla c = cuadrilla(1);
        Operario capataz = operario(2, "ACTIVO", cuadrilla(2));
        capataz.setRolOperativo("CAPATAZ");
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(operarioRepository.findByKeycloakUsername("capataz")).thenReturn(Optional.of(capataz));

        assertThatThrownBy(() -> service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "capataz"))
                .isInstanceOf(AccessDeniedException.class);
        verify(cuadrillaRepository, never()).save(any());
    }

    @Test
    void checkInDeAlguienQueNoEsCapataz_esAccesoDenegado() {
        Cuadrilla c = cuadrilla(1);
        Operario peon = operario(2, "ACTIVO", c);
        peon.setRolOperativo("PEON");
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(operarioRepository.findByKeycloakUsername("operario")).thenReturn(Optional.of(peon));

        assertThatThrownBy(() -> service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "operario"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkInDeUnUsuarioSinOperario_esAccesoDenegado() {
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(cuadrilla(1)));
        when(operarioRepository.findByKeycloakUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "fantasma"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkInDeCuadrillaInexistente_lanza404() {
        when(cuadrillaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarUbicacion(99L, new UbicacionRequest(-32.0, -65.0), "capataz"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
