package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.UbicacionRequest;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Maquina;
import com.obrasviales.ms_recursos.entity.Operario;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Consulta de cuadrillas y check-in del capataz: solo el CAPATAZ de esa cuadrilla puede mover su ubicación,
// que es la que usa RN-07 para calcular el viaje al tramo.
@ExtendWith(MockitoExtension.class)
class CuadrillaServiceTest {

    @Mock
    private CuadrillaRepository cuadrillaRepository;
    @Mock
    private OperarioRepository operarioRepository;
    @Mock
    private MaquinaRepository maquinaRepository;

    @InjectMocks
    private CuadrillaService service;

    @BeforeEach
    void preparar() {
        lenient().when(cuadrillaRepository.save(any(Cuadrilla.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(operarioRepository.findByCuadrillaId(anyLong())).thenReturn(List.of());
        lenient().when(maquinaRepository.findByCuadrillaId(anyLong())).thenReturn(List.of());
    }

    private Cuadrilla cuadrilla(long id) {
        Cuadrilla c = new Cuadrilla();
        c.setId(id);
        c.setNombre("Cuadrilla " + id);
        return c;
    }

    private Operario operario(long id, String rol, Cuadrilla cuadrilla) {
        Operario o = new Operario();
        o.setId(id);
        o.setNombre("Operario " + id);
        o.setRolOperativo(rol);
        o.setCuadrilla(cuadrilla);
        return o;
    }

    @Test
    void listarInformaOperariosYMaquinasDeCadaCuadrilla() {
        Cuadrilla alfa = cuadrilla(1);
        Maquina m = new Maquina();
        m.setId(5L);
        when(cuadrillaRepository.findAll()).thenReturn(List.of(alfa));
        when(operarioRepository.findByCuadrillaId(1L)).thenReturn(List.of(operario(2, "PEON", alfa)));
        when(maquinaRepository.findByCuadrillaId(1L)).thenReturn(List.of(m));

        List<CuadrillaResponse> lista = service.listar();

        assertThat(lista).singleElement().satisfies(c -> {
            assertThat(c.operarioIds()).containsExactly(2L);
            assertThat(c.maquinaIds()).containsExactly(5L);
        });
    }

    @Test
    void checkInDelCapatazDeLaCuadrilla_actualizaLaUbicacion() {
        Cuadrilla c = cuadrilla(1);
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(operarioRepository.findByKeycloakUsername("capataz")).thenReturn(Optional.of(operario(2, "CAPATAZ", c)));

        CuadrillaResponse r = service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "capataz");

        assertThat(r.ultimaLatitud()).isEqualTo(-32.0);
        assertThat(r.ultimaLongitud()).isEqualTo(-65.0);
        assertThat(c.getFechaCheckin()).isNotNull();
    }

    @Test
    void checkInDeUnCapatazDeOtraCuadrilla_esAccesoDenegado() {
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(cuadrilla(1)));
        when(operarioRepository.findByKeycloakUsername("capataz"))
                .thenReturn(Optional.of(operario(2, "CAPATAZ", cuadrilla(2))));

        assertThatThrownBy(() -> service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "capataz"))
                .isInstanceOf(AccessDeniedException.class);
        verify(cuadrillaRepository, never()).save(any());
    }

    @Test
    void checkInDeAlguienQueNoEsCapataz_esAccesoDenegado() {
        Cuadrilla c = cuadrilla(1);
        when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(operarioRepository.findByKeycloakUsername("peon")).thenReturn(Optional.of(operario(2, "PEON", c)));

        assertThatThrownBy(() -> service.registrarUbicacion(1L, new UbicacionRequest(-32.0, -65.0), "peon"))
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
