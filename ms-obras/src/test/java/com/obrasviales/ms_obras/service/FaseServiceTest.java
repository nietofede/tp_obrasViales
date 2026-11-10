package com.obrasviales.ms_obras.service;

import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.repository.FaseRepository;
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

@ExtendWith(MockitoExtension.class)
class FaseServiceTest {

    @Mock
    private FaseRepository faseRepository;

    @InjectMocks
    private FaseService faseService;

    private Fase crearFase(Long id, EstadoFase estado, Fase anterior) {
        Fase fase = new Fase();
        fase.setId(id);
        fase.setEstado(estado);
        fase.setFaseAnterior(anterior);
        return fase;
    }

    // RN-01 caso normal: la fase anterior está completada, entonces inicia
    @Test
    void iniciaFaseCuandoLaAnteriorEstaCompletada() {
        Fase anterior = crearFase(1L, EstadoFase.COMPLETADA, null);
        Fase fase = crearFase(2L, EstadoFase.PENDIENTE, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));

        Fase resultado = faseService.iniciarFase(2L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_CURSO);
    }

    // RN-01 caso límite: la primera fase del tramo no tiene anterior, entonces inicia
    @Test
    void iniciaPrimeraFaseSinAnterior() {
        Fase primera = crearFase(1L, EstadoFase.PENDIENTE, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(primera));
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));

        Fase resultado = faseService.iniciarFase(1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_CURSO);
    }

    // RN-01 rechazo: la anterior no está completada, entonces rechaza y no guarda nada
    @Test
    void rechazaIniciarFaseSiLaAnteriorNoEstaCompletada() {
        Fase anterior = crearFase(1L, EstadoFase.EN_CURSO, null);
        Fase fase = crearFase(2L, EstadoFase.PENDIENTE, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.iniciarFase(2L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("anterior no está completada");

        verify(faseRepository, never()).save(any(Fase.class));
        assertThat(fase.getEstado()).isEqualTo(EstadoFase.PENDIENTE);
    }
}