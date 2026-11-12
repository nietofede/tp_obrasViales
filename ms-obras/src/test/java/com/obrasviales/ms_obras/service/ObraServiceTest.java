package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.repository.ObraRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObraServiceTest {

    @Mock
    private ObraRepository obraRepository;

    @Mock
    private TramoRepository tramoRepository;

    @InjectMocks
    private ObraService obraService;

    // Crear obra: guarda el nombre y la descripción y devuelve la obra guardada
    @Test
    void crearObraGuardaNombreYDescripcion() {
        when(obraRepository.save(any(Obra.class))).thenAnswer(inv -> inv.getArgument(0));

        Obra resultado = obraService.crearObra("Repavimentacion Av. Libertad", "Tramo norte");

        assertThat(resultado.getNombre()).isEqualTo("Repavimentacion Av. Libertad");
        assertThat(resultado.getDescripcion()).isEqualTo("Tramo norte");
        verify(obraRepository).save(any(Obra.class));
    }

    // Crear tramo con obra existente: el tramo queda asociado a su obra
    @Test
    void crearTramoAsociaElTramoALaObra() {
        Obra obra = new Obra();
        obra.setId(5L);
        when(obraRepository.findById(5L)).thenReturn(Optional.of(obra));
        when(tramoRepository.save(any(Tramo.class))).thenAnswer(inv -> inv.getArgument(0));

        Tramo resultado = obraService.crearTramo(
                5L, "Tramo norte", new BigDecimal("1000.00"), -34.6, -58.4);

        assertThat(resultado.getObra()).isSameAs(obra);
        assertThat(resultado.getNombre()).isEqualTo("Tramo norte");
        assertThat(resultado.getSuperficieM2()).isEqualByComparingTo("1000.00");
        assertThat(resultado.getLatitud()).isEqualTo(-34.6);
        assertThat(resultado.getLongitud()).isEqualTo(-58.4);
    }

    // Rechazo: crear un tramo en una obra inexistente lanza 404 y no guarda nada
    @Test
    void crearTramoEnObraInexistenteLanzaNoEncontradoYNoGuarda() {
        when(obraRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.crearTramo(
                99L, "Tramo norte", new BigDecimal("1000.00"), -34.6, -58.4))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(tramoRepository, never()).save(any(Tramo.class));
    }
}