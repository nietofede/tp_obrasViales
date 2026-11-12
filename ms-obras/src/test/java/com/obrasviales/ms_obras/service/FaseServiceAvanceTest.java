package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class FaseServiceAvanceTest {

    @Mock
    private FaseRepository faseRepository;

    @Mock
    private TramoRepository tramoRepository;

    @Mock
    private MaterialService materialService;

    @InjectMocks
    private FaseService faseService;

    private Fase fase(Long id, EstadoFase estado, int avance, Fase anterior) {
        Fase fase = new Fase();
        fase.setId(id);
        fase.setEstado(estado);
        fase.setAvancePorcentaje(avance);
        fase.setFaseAnterior(anterior);
        return fase;
    }

    private void guardarDevuelveLoMismo() {
        when(faseRepository.save(any(Fase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- registrarAvance ----------

    // Caso normal: el primer avance inicia la fase si la anterior esta completada
    @Test
    void elPrimerAvanceIniciaLaFaseSiLaAnteriorEstaCompletada() {
        Fase anterior = fase(1L, EstadoFase.COMPLETADA, 100, null);
        Fase fase = fase(2L, EstadoFase.PENDIENTE, 0, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));
        guardarDevuelveLoMismo();

        Fase resultado = faseService.registrarAvance(2L, 40);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_CURSO);
        assertThat(resultado.getAvancePorcentaje()).isEqualTo(40);
    }

    // Limite: una fase sin anterior tambien se inicia con el primer avance
    @Test
    void elPrimerAvanceIniciaLaPrimeraFaseDelTramoSinAnterior() {
        Fase fase = fase(1L, EstadoFase.PENDIENTE, 0, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        guardarDevuelveLoMismo();

        Fase resultado = faseService.registrarAvance(1L, 10);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_CURSO);
    }

    // Limite: al llegar a 100 la fase queda COMPLETADA
    @Test
    void unAvanceDel100CompletaLaFase() {
        Fase fase = fase(1L, EstadoFase.EN_CURSO, 60, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        guardarDevuelveLoMismo();

        Fase resultado = faseService.registrarAvance(1L, 100);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.COMPLETADA);
        assertThat(resultado.getAvancePorcentaje()).isEqualTo(100);
    }

    // Limite: repetir el mismo porcentaje es aceptado y no cambia el estado
    @Test
    void repetirElMismoAvanceEsAceptado() {
        Fase fase = fase(1L, EstadoFase.EN_CURSO, 50, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        guardarDevuelveLoMismo();

        Fase resultado = faseService.registrarAvance(1L, 50);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_CURSO);
        assertThat(resultado.getAvancePorcentaje()).isEqualTo(50);
    }

    // Rechazo RN-01: la anterior no esta completada
    @Test
    void elPrimerAvanceEsRechazadoSiLaAnteriorNoEstaCompletada() {
        Fase anterior = fase(1L, EstadoFase.EN_CURSO, 30, null);
        Fase fase = fase(2L, EstadoFase.PENDIENTE, 0, anterior);
        when(faseRepository.findById(2L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.registrarAvance(2L, 20))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("RN-01");

        verify(faseRepository, never()).save(any(Fase.class));
        assertThat(fase.getEstado()).isEqualTo(EstadoFase.PENDIENTE);
    }

    // Rechazo: una fase COMPLETADA no admite mas avances
    @Test
    void unAvanceEnUnaFaseCompletadaEsConflicto() {
        Fase fase = fase(1L, EstadoFase.COMPLETADA, 100, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.registrarAvance(1L, 100))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("COMPLETADA");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: una fase EN_RIESGO primero se reprograma
    @Test
    void unAvanceEnUnaFaseEnRiesgoEsConflicto() {
        Fase fase = fase(1L, EstadoFase.EN_RIESGO, 20, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.registrarAvance(1L, 30))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("EN_RIESGO");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    // Rechazo: el avance no puede retroceder
    @Test
    void unAvanceQueRetrocedeEsRechazadoConElCodigoAvance() {
        Fase fase = fase(1L, EstadoFase.EN_CURSO, 60, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.registrarAvance(1L, 40))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("AVANCE"));

        verify(faseRepository, never()).save(any(Fase.class));
        assertThat(fase.getAvancePorcentaje()).isEqualTo(60);
    }

    // Rechazo: fase inexistente
    @Test
    void unAvanceEnUnaFaseInexistenteEs404() {
        when(faseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faseService.registrarAvance(99L, 10))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    // ---------- marcarEnRiesgo ----------

    @Test
    void marcarEnRiesgoCambiaElEstado() {
        Fase fase = fase(1L, EstadoFase.PENDIENTE, 0, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        guardarDevuelveLoMismo();

        Fase resultado = faseService.marcarEnRiesgo(1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_RIESGO);
    }

    // Limite: marcarla de nuevo es idempotente y no guarda otra vez
    @Test
    void marcarEnRiesgoUnaFaseQueYaEstaEnRiesgoEsIdempotente() {
        Fase fase = fase(1L, EstadoFase.EN_RIESGO, 0, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        Fase resultado = faseService.marcarEnRiesgo(1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.EN_RIESGO);
        verify(faseRepository, never()).save(any(Fase.class));
    }

    @Test
    void marcarEnRiesgoUnaFaseCompletadaEsConflicto() {
        Fase fase = fase(1L, EstadoFase.COMPLETADA, 100, null);
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));

        assertThatThrownBy(() -> faseService.marcarEnRiesgo(1L))
                .isInstanceOf(ConflictoEstadoException.class)
                .hasMessageContaining("COMPLETADA");

        verify(faseRepository, never()).save(any(Fase.class));
    }

    @Test
    void marcarEnRiesgoUnaFaseInexistenteEs404() {
        when(faseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faseService.marcarEnRiesgo(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- reprogramar una fase en riesgo ----------

    @Test
    void reprogramarUnaFaseEnRiesgoLaDevuelveAPendiente() {
        Fase fase = fase(1L, EstadoFase.EN_RIESGO, 30, null);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 1));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 5));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        when(faseRepository.findByFaseAnteriorId(1L)).thenReturn(List.of());
        guardarDevuelveLoMismo();

        Fase resultado = faseService.reprogramarFase(1L, LocalDate.of(2026, 12, 10));

        assertThat(resultado.getEstado()).isEqualTo(EstadoFase.PENDIENTE);
        assertThat(resultado.getFechaInicioPlanificada()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(resultado.getFechaFinPlanificada()).isEqualTo(LocalDate.of(2026, 12, 14));
    }

    // ---------- consultas ----------

    @Test
    void obtenerUnaFaseInexistenteEs404() {
        when(faseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faseService.obtenerFase(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    // RN-03: los materiales de una fase salen del tipo de fase y de la superficie de su tramo
    @Test
    void losMaterialesDeUnaFaseUsanSuTipoYLaSuperficieDeSuTramo() {
        Tramo tramo = new Tramo();
        tramo.setSuperficieM2(new BigDecimal("1000.00"));
        Fase fase = fase(1L, EstadoFase.PENDIENTE, 0, null);
        fase.setTipo(TipoFase.BASE);
        fase.setTramo(tramo);
        List<MaterialCalculado> materiales = List.of(
                new MaterialCalculado("Piedra triturada", new BigDecimal("250.00"), "m3"));
        when(faseRepository.findById(1L)).thenReturn(Optional.of(fase));
        when(materialService.calcularMateriales(TipoFase.BASE, new BigDecimal("1000.00")))
                .thenReturn(materiales);

        assertThat(faseService.materialesDeFase(1L)).isEqualTo(materiales);
    }
}
