package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CronogramaServiceTest {

    @Mock
    private TramoRepository tramoRepository;

    @Mock
    private FaseRepository faseRepository;

    @InjectMocks
    private CronogramaService cronogramaService;

    private Fase fase(Long id, TipoFase tipo, LocalDate inicio, LocalDate fin) {
        Fase fase = new Fase();
        fase.setId(id);
        fase.setTipo(tipo);
        fase.setEstado(EstadoFase.PENDIENTE);
        fase.setFechaInicioPlanificada(inicio);
        fase.setFechaFinPlanificada(fin);
        return fase;
    }

    // Caso normal: devuelve las fases del tramo en el orden que entrega el repository
    @Test
    void devuelveLasFasesDelTramoEnOrden() {
        when(tramoRepository.existsById(10L)).thenReturn(true);
        when(faseRepository.findByTramoIdOrderByFechaInicioPlanificadaAsc(10L)).thenReturn(List.of(
                fase(1L, TipoFase.BASE, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)),
                fase(2L, TipoFase.SUBBASE, LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9))));

        List<FaseResponse> resultado = cronogramaService.cronogramaDelTramo(10L);

        assertThat(resultado).extracting(FaseResponse::id).containsExactly(1L, 2L);
        assertThat(resultado.get(0).tipo()).isEqualTo(TipoFase.BASE);
    }

    // Caso límite: un tramo sin fases devuelve una lista vacía, no un error
    @Test
    void devuelveListaVaciaSiElTramoNoTieneFases() {
        when(tramoRepository.existsById(10L)).thenReturn(true);
        when(faseRepository.findByTramoIdOrderByFechaInicioPlanificadaAsc(10L)).thenReturn(List.of());

        assertThat(cronogramaService.cronogramaDelTramo(10L)).isEmpty();
    }

    // Rechazo: un tramo inexistente lanza 404 y ni siquiera consulta las fases
    @Test
    void rechazaUnTramoInexistente() {
        when(tramoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> cronogramaService.cronogramaDelTramo(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(faseRepository, never()).findByTramoIdOrderByFechaInicioPlanificadaAsc(99L);
    }
}