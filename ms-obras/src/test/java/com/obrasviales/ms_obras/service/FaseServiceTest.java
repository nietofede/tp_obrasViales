package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
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

    @Mock
    private TramoRepository tramoRepository;

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

    private Tramo tramo(Long id) {
        Tramo tramo = new Tramo();
        tramo.setId(id);
        return tramo;
    }

    private Fase faseEnTramo(Long id, Long tramoId, LocalDate inicio, LocalDate fin) {
        Fase fase = crearFaseConFechas(id, EstadoFase.PENDIENTE, null, inicio, fin);
        fase.setTramo(tramo(tramoId));
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
                .isInstanceOf(ConflictoEstadoException.class)
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

    // ---------- Crear fase ----------

    // Caso normal: la primera fase del tramo se crea PENDIENTE y sin anterior
    @Test
    void creaLaPrimeraFaseDelTramoPendienteYSinAnterior() {
        Tramo tramo = tramo(10L);
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo));
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));

        Fase resultado = faseService.crearFase(10L, TipoFase.RELEVAMIENTO,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), null);

        assertThat(resultado.getTramo()).isSameAs(tramo);
        assertThat(resultado.getTipo()).isEqualTo(TipoFase.RELEVAMIENTO);
        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.PENDIENTE);
        assertThat(resultado.getFaseAnterior()).isNull();
    }

    // Caso límite: la nueva fase empieza el mismo día en que termina su anterior
    @Test
    void creaFaseQueEmpiezaElMismoDiaQueTerminaLaAnterior() {
        Tramo tramo = tramo(10L);
        Fase anterior = faseEnTramo(1L, 10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(anterior));
        when(faseRepository.findByFaseAnteriorId(1L)).thenReturn(List.of());
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));

        Fase resultado = faseService.crearFase(10L, TipoFase.BASE,
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 9), 1L);

        assertThat(resultado.getFaseAnterior()).isSameAs(anterior);
        assertThat(resultado.getTramo()).isSameAs(tramo);
    }

    // Rechazo: el tramo no existe
    @Test
    void rechazaCrearFaseEnTramoInexistente() {
        when(tramoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faseService.crearFase(99L, TipoFase.BASE,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: la fecha de fin es anterior a la de inicio
    @Test
    void rechazaCrearFaseConFinAnteriorAlInicio() {
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo(10L)));

        assertThatThrownBy(() -> faseService.crearFase(10L, TipoFase.BASE,
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 1), null))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("FASE-FECHAS"));

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: la fase anterior no existe
    @Test
    void rechazaCrearFaseConAnteriorInexistente() {
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo(10L)));
        when(faseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faseService.crearFase(10L, TipoFase.BASE,
                LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9), 77L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("77");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: la fase anterior pertenece a otro tramo
    @Test
    void rechazaCrearFaseConAnteriorDeOtroTramo() {
        Fase anteriorDeOtroTramo = faseEnTramo(1L, 99L,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo(10L)));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(anteriorDeOtroTramo));

        assertThatThrownBy(() -> faseService.crearFase(10L, TipoFase.BASE,
                LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9), 1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("FASE-TRAMO"));

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: la fase anterior ya tiene una fase siguiente (la cadena es lineal)
    @Test
    void rechazaCrearFaseSiLaAnteriorYaTieneSiguiente() {
        Fase anterior = faseEnTramo(1L, 10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        Fase yaSiguiente = faseEnTramo(2L, 10L, LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9));
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo(10L)));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(anterior));
        when(faseRepository.findByFaseAnteriorId(1L)).thenReturn(List.of(yaSiguiente));

        assertThatThrownBy(() -> faseService.crearFase(10L, TipoFase.SUBBASE,
                LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 9), 1L))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("ya tiene una fase siguiente");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: la nueva fase empieza antes de que termine su anterior
    @Test
    void rechazaCrearFaseQueEmpiezaAntesDeQueTermineLaAnterior() {
        Fase anterior = faseEnTramo(1L, 10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        when(tramoRepository.findById(10L)).thenReturn(Optional.of(tramo(10L)));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(anterior));
        when(faseRepository.findByFaseAnteriorId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> faseService.crearFase(10L, TipoFase.BASE,
                LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 9), 1L))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("RN-02"));

        verify(faseRepository, never()).save(any(Fase.class));
    }
}