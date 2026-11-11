package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.repository.FaseRepository;
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

    private Fase crearFaseConFechas(Long id, EstadoFase estado, Fase anterior,
                                    LocalDate inicio, LocalDate fin) {
        Fase fase = crearFase(id, estado, anterior);
        fase.setFechaInicioPlanificada(inicio);
        fase.setFechaFinPlanificada(fin);
        return fase;
    }

    // ---------- RN-01 ----------

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
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("RN-01"))
                .hasMessageContaining("anterior no está completada");

        verify(faseRepository, never()).save(any(Fase.class));
        assertThat(fase.getEstado()).isEqualTo(EstadoFase.PENDIENTE);
    }

    // ---------- RN-02 ----------

    // RN-02 caso normal: al mover la base 3 días, se corren subbase y carpeta en cascada
    @Test
    void reprogramarFaseCorreEnCascadaLasDependientes() {
        Fase base = crearFaseConFechas(1L, EstadoFase.PENDIENTE, null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15));
        Fase subbase = crearFaseConFechas(2L, EstadoFase.PENDIENTE, base,
                LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 20));
        Fase carpeta = crearFaseConFechas(3L, EstadoFase.PENDIENTE, subbase,
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 23));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(base));
        when(faseRepository.findByFaseAnteriorId(1L)).thenReturn(List.of(subbase));
        when(faseRepository.findByFaseAnteriorId(2L)).thenReturn(List.of(carpeta));
        when(faseRepository.findByFaseAnteriorId(3L)).thenReturn(List.of());

        faseService.reprogramarFase(1L, LocalDate.of(2026, 12, 13));

        assertThat(base.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 13));
        assertThat(base.getFechaFinPlanificada()).isEqualTo(LocalDate.of(2026, 12, 18));
        assertThat(subbase.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 19));
        assertThat(subbase.getFechaFinPlanificada()).isEqualTo(LocalDate.of(2026, 12, 23));
        assertThat(carpeta.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(carpeta.getFechaFinPlanificada()).isEqualTo(LocalDate.of(2026, 12, 26));
    }

    // RN-02 caso límite: la subbase puede empezar el mismo día en que termina la base
    @Test
    void reprogramarFasePermiteIniciarElMismoDiaQueTerminaLaAnterior() {
        Fase base = crearFaseConFechas(1L, EstadoFase.COMPLETADA, null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15));
        Fase subbase = crearFaseConFechas(2L, EstadoFase.PENDIENTE, base,
                LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 20));
        when(faseRepository.findById(2L)).thenReturn(Optional.of(subbase));
        when(faseRepository.findByFaseAnteriorId(2L)).thenReturn(List.of());

        faseService.reprogramarFase(2L, LocalDate.of(2026, 12, 15));

        assertThat(subbase.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(subbase.getFechaFinPlanificada()).isEqualTo(LocalDate.of(2026, 12, 19));
    }

    // RN-02 rechazo: una fase COMPLETADA no se puede reprogramar (conflicto de estado)
    @Test
    void rechazaReprogramarUnaFaseCompletada() {
        Fase fase = crearFaseConFechas(1L, EstadoFase.COMPLETADA, null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.reprogramarFase(1L, LocalDate.of(2026, 12, 20)))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("COMPLETADA");

        verify(faseRepository, never()).save(any(Fase.class));
        assertThat(fase.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    // RN-02 rechazo: no se puede iniciar antes de que termine la fase anterior
    @Test
    void rechazaReprogramarAntesDeQueTermineLaFaseAnterior() {
        Fase base = crearFaseConFechas(1L, EstadoFase.EN_CURSO, null,
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15));
        Fase subbase = crearFaseConFechas(2L, EstadoFase.PENDIENTE, base,
                LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 20));
        when(faseRepository.findById(2L)).thenReturn(Optional.of(subbase));

        assertThatThrownBy(() -> faseService.reprogramarFase(2L, LocalDate.of(2026, 12, 14)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("RN-02"))
                .hasMessageContaining("antes de que termine la fase anterior");

        verify(faseRepository, never()).save(any(Fase.class));
    }
}