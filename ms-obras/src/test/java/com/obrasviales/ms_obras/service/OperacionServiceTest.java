package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.DatosOperacionResponse;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.repository.FaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperacionServiceTest {

    @Mock
    private FaseRepository faseRepository;

    @Mock
    private MaterialService materialService;

    @InjectMocks
    private OperacionService operacionService;

    private Tramo tramo() {
        Tramo tramo = new Tramo();
        tramo.setId(10L);
        tramo.setNombre("Tramo Av. Libertad");
        tramo.setSuperficieM2(new BigDecimal("1000.00"));
        tramo.setLatitud(-34.6);
        tramo.setLongitud(-58.4);
        return tramo;
    }

    private Fase fase(Long id, TipoFase tipo, EstadoFase estado, Fase anterior) {
        Fase fase = new Fase();
        fase.setId(id);
        fase.setTipo(tipo);
        fase.setEstado(estado);
        fase.setTramo(tramo());
        fase.setFaseAnterior(anterior);
        return fase;
    }

    // Caso normal: fase sin anterior, devuelve tramo, coordenadas y materiales de RN-03
    @Test
    void devuelveLosDatosDeUnaFaseSinAnterior() {
        Fase fase = fase(1L, TipoFase.BASE, EstadoFase.PENDIENTE, null);
        List<MaterialCalculado> materiales =
                List.of(new MaterialCalculado("Piedra triturada", new BigDecimal("250.00"), "m3"));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        when(materialService.calcularMateriales(TipoFase.BASE, new BigDecimal("1000.00")))
                .thenReturn(materiales);

        DatosOperacionResponse resultado = operacionService.obtenerDatosOperacion(1L);

        assertThat(resultado.faseId()).isEqualTo(1L);
        assertThat(resultado.tipoTrabajo()).isEqualTo(TipoFase.BASE);
        assertThat(resultado.tramoId()).isEqualTo(10L);
        assertThat(resultado.tramoNombre()).isEqualTo("Tramo Av. Libertad");
        assertThat(resultado.latitud()).isEqualTo(-34.6);
        assertThat(resultado.longitud()).isEqualTo(-58.4);
        assertThat(resultado.materiales()).isEqualTo(materiales);
        verify(materialService).calcularMateriales(TipoFase.BASE, new BigDecimal("1000.00"));
    }

    // Caso límite: la primera fase del tramo no tiene anterior, cuenta como completada
    @Test
    void laFaseSinAnteriorCuentaComoAnteriorCompletada() {
        Fase fase = fase(1L, TipoFase.RELEVAMIENTO, EstadoFase.PENDIENTE, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        when(materialService.calcularMateriales(TipoFase.RELEVAMIENTO, new BigDecimal("1000.00")))
                .thenReturn(List.of());

        DatosOperacionResponse resultado = operacionService.obtenerDatosOperacion(1L);

        assertThat(resultado.faseAnteriorCompletada()).isTrue();
        assertThat(resultado.materiales()).isEmpty();
    }

    // La fase anterior está completada: Operaciones puede avanzar
    @Test
    void informaQueLaAnteriorEstaCompletada() {
        Fase anterior = fase(1L, TipoFase.BASE, EstadoFase.COMPLETADA, null);
        Fase fase = fase(2L, TipoFase.SUBBASE, EstadoFase.PENDIENTE, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));
        when(materialService.calcularMateriales(TipoFase.SUBBASE, new BigDecimal("1000.00")))
                .thenReturn(List.of());

        DatosOperacionResponse resultado = operacionService.obtenerDatosOperacion(2L);

        assertThat(resultado.faseAnteriorCompletada()).isTrue();
    }

    // La fase anterior NO está completada: lo informa en vez de rechazar
    @Test
    void informaQueLaAnteriorNoEstaCompletada() {
        Fase anterior = fase(1L, TipoFase.BASE, EstadoFase.EN_CURSO, null);
        Fase fase = fase(2L, TipoFase.SUBBASE, EstadoFase.PENDIENTE, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));
        when(materialService.calcularMateriales(TipoFase.SUBBASE, new BigDecimal("1000.00")))
                .thenReturn(List.of());

        DatosOperacionResponse resultado = operacionService.obtenerDatosOperacion(2L);

        assertThat(resultado.faseAnteriorCompletada()).isFalse();
    }

    // Rechazo: una fase inexistente lanza 404 (vía la excepción de commons)
    @Test
    void rechazaUnaFaseInexistente() {
        when(faseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operacionService.obtenerDatosOperacion(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }
}