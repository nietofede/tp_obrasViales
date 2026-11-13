package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionResponse;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoResponse;
import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import com.obrasviales.ms_recursos.entity.ConfiguracionDesplazamiento;
import com.obrasviales.ms_recursos.repository.ComposicionMinimaRepository;
import com.obrasviales.ms_recursos.repository.ConfiguracionDesplazamientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// RN-06, RN-07 y RN-08 dependen de estos parámetros: se comprueba que el ADMIN pueda cambiarlos y que no
// se acepten combinaciones incoherentes.
@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceTest {

    @Mock
    private ComposicionMinimaRepository composicionRepository;
    @Mock
    private ConfiguracionDesplazamientoRepository desplazamientoRepository;

    @InjectMocks
    private ConfiguracionService service;

    // ---------- Composición mínima ----------

    @Test
    void tipoDeTrabajoDesconocido_lanza404() {
        assertThatThrownBy(() -> service.actualizarComposicion("PAVIMENTACION",
                new ComposicionRequest(2, null, 0, false)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(composicionRepository, never()).save(any());
    }

    @Test
    void pedirMaquinasSinIndicarElTipo_lanza422() {
        assertThatThrownBy(() -> service.actualizarComposicion("BASE", new ComposicionRequest(2, "  ", 1, false)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigoRegla()).isEqualTo("RN-06"));
    }

    @Test
    void actualizaLaComposicionExistente() {
        ComposicionMinimaTrabajo existente = new ComposicionMinimaTrabajo();
        existente.setTipoTrabajo("BASE");
        existente.setMinOperarios(2);
        when(composicionRepository.findById("BASE")).thenReturn(Optional.of(existente));
        when(composicionRepository.save(any(ComposicionMinimaTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        ComposicionResponse r = service.actualizarComposicion("BASE",
                new ComposicionRequest(4, "RODILLO_COMPACTADOR", 2, false));

        assertThat(r.tipoTrabajo()).isEqualTo("BASE");
        assertThat(r.minOperarios()).isEqualTo(4);
        assertThat(r.cantidadMaquinas()).isEqualTo(2);
        assertThat(r.tipoMaquinaRequerida()).isEqualTo("RODILLO_COMPACTADOR");
    }

    @Test
    void creaLaComposicionSiElTipoValidoTodaviaNoTeniaFila() {
        when(composicionRepository.findById("LOSA_HORMIGON")).thenReturn(Optional.empty());
        when(composicionRepository.save(any(ComposicionMinimaTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        ComposicionResponse r = service.actualizarComposicion("LOSA_HORMIGON",
                new ComposicionRequest(3, null, 0, true));

        assertThat(r.tipoTrabajo()).isEqualTo("LOSA_HORMIGON");
        assertThat(r.requiereMezcla()).isTrue();
        assertThat(r.tipoMaquinaRequerida()).isNull();
    }

    @Test
    void sinMaquinasElTipoVacioSeGuardaComoNulo() {
        when(composicionRepository.findById("RELEVAMIENTO")).thenReturn(Optional.empty());
        when(composicionRepository.save(any(ComposicionMinimaTrabajo.class))).thenAnswer(inv -> inv.getArgument(0));

        ComposicionResponse r = service.actualizarComposicion("RELEVAMIENTO",
                new ComposicionRequest(2, "  ", 0, false));

        assertThat(r.tipoMaquinaRequerida()).isNull();
    }

    // ---------- Desplazamiento ----------

    @Test
    void viajeMaximoMayorQueLaJornada_lanza422Rn07() {
        assertThatThrownBy(() -> service.actualizarDesplazamiento(new DesplazamientoRequest(600, 480, 60, 15)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigoRegla()).isEqualTo("RN-07"));
        verify(desplazamientoRepository, never()).save(any());
    }

    @Test
    void actualizaLosCuatroParametros() {
        ConfiguracionDesplazamiento config = new ConfiguracionDesplazamiento();
        when(desplazamientoRepository.findById(1L)).thenReturn(Optional.of(config));
        when(desplazamientoRepository.save(any(ConfiguracionDesplazamiento.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DesplazamientoResponse r = service.actualizarDesplazamiento(new DesplazamientoRequest(120, 540, 45, 10));

        assertThat(r.viajeMaximoCuadrillaMin()).isEqualTo(120);
        assertThat(r.duracionJornadaMin()).isEqualTo(540);
        assertThat(r.ventanaTransporteMezclaMin()).isEqualTo(45);
        assertThat(r.margenCamionMin()).isEqualTo(10);
    }

    @Test
    void sinFilaDeConfiguracion_lanza404() {
        when(desplazamientoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerDesplazamiento())
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
