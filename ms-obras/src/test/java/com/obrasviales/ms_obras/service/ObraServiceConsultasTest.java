package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.ObraDetalleResponse;
import com.obrasviales.ms_obras.dto.ObraResumenResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.ObraRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

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
class ObraServiceConsultasTest {

    @Mock
    private ObraRepository obraRepository;

    @Mock
    private TramoRepository tramoRepository;

    @Mock
    private FaseRepository faseRepository;

    @InjectMocks
    private ObraService obraService;

    private Obra obra(Long id, String nombre) {
        Obra obra = new Obra();
        obra.setId(id);
        obra.setNombre(nombre);
        return obra;
    }

    private Tramo tramo(Long id, Obra obra, String nombre) {
        Tramo tramo = new Tramo();
        tramo.setId(id);
        tramo.setObra(obra);
        tramo.setNombre(nombre);
        tramo.setSuperficieM2(new BigDecimal("1000.00"));
        tramo.setLatitud(-34.6);
        tramo.setLongitud(-58.4);
        return tramo;
    }

    private Fase fase(Long id, Tramo tramo, EstadoFase estado, int avance) {
        Fase fase = new Fase();
        fase.setId(id);
        fase.setTramo(tramo);
        fase.setTipo(TipoFase.BASE);
        fase.setEstado(estado);
        fase.setAvancePorcentaje(avance);
        fase.setFechaInicioPlanificada(LocalDate.of(2026, 12, 1));
        fase.setFechaFinPlanificada(LocalDate.of(2026, 12, 5));
        return fase;
    }

    // ---------- listar obras (con estado y avance) ----------

    // Caso normal: el avance es el promedio de sus fases y el estado se deriva de ellas
    @Test
    void listarObrasCalculaElEstadoYElAvanceDeCadaObra() {
        Obra obra1 = obra(1L, "Ruta 34");
        Obra obra2 = obra(2L, "Av. Libertad");
        Tramo tramo = tramo(20L, obra1, "Tramo norte");
        when(obraRepository.findAll(any(Sort.class))).thenReturn(List.of(obra1, obra2));
        when(faseRepository.findByTramoObraId(1L)).thenReturn(List.of(
                fase(30L, tramo, EstadoFase.COMPLETADA, 100),
                fase(31L, tramo, EstadoFase.PENDIENTE, 0)));
        when(faseRepository.findByTramoObraId(2L)).thenReturn(List.of());

        List<ObraResumenResponse> resultado = obraService.listarObras();

        assertThat(resultado).extracting(ObraResumenResponse::id).containsExactly(1L, 2L);
        assertThat(resultado.get(0).avance()).isEqualTo(50);
        assertThat(resultado.get(0).estado()).isEqualTo("EN_EJECUCION");
        assertThat(resultado.get(1).avance()).isZero();
        assertThat(resultado.get(1).estado()).isEqualTo("PLANIFICADA");
    }

    // Limite: con todas las fases completadas la obra esta COMPLETADA al 100 %
    @Test
    void unaObraConTodasSusFasesCompletadasEstaCompletada() {
        Obra obra = obra(1L, "Ruta 34");
        Tramo tramo = tramo(20L, obra, "Tramo norte");
        when(obraRepository.findAll(any(Sort.class))).thenReturn(List.of(obra));
        when(faseRepository.findByTramoObraId(1L)).thenReturn(List.of(
                fase(30L, tramo, EstadoFase.COMPLETADA, 100),
                fase(31L, tramo, EstadoFase.COMPLETADA, 100)));

        ObraResumenResponse resultado = obraService.listarObras().get(0);

        assertThat(resultado.estado()).isEqualTo("COMPLETADA");
        assertThat(resultado.avance()).isEqualTo(100);
    }

    // Limite: sin obras devuelve una lista vacia
    @Test
    void listarObrasSinObrasDevuelveListaVacia() {
        when(obraRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(obraService.listarObras()).isEmpty();
    }

    // ---------- ver una obra (con tramos y fases) ----------

    @Test
    void obtenerObraDevuelveSusTramosConSusFases() {
        Obra obra = obra(5L, "Ruta 34");
        Tramo tramo = tramo(20L, obra, "Tramo norte");
        when(obraRepository.findById(5L)).thenReturn(Optional.of(obra));
        when(faseRepository.findByTramoObraIdOrderByFechaInicioPlanificadaAsc(5L))
                .thenReturn(List.of(fase(30L, tramo, EstadoFase.EN_CURSO, 40)));
        when(tramoRepository.findByObraIdOrderByIdAsc(5L)).thenReturn(List.of(tramo));

        ObraDetalleResponse resultado = obraService.obtenerObra(5L);

        assertThat(resultado.estado()).isEqualTo("EN_EJECUCION");
        assertThat(resultado.avance()).isEqualTo(40);
        assertThat(resultado.tramos()).hasSize(1);
        assertThat(resultado.tramos().get(0).tramo().nombre()).isEqualTo("Tramo norte");
        assertThat(resultado.tramos().get(0).fases()).extracting(FaseResponse::id).containsExactly(30L);
    }

    @Test
    void obtenerObraInexistenteEs404() {
        when(obraRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.obtenerObra(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    // ---------- editar una obra ----------

    // ---------- tramos ----------

    @Test
    void listarTramosDevuelveLosTramosDeLaObra() {
        Obra obra = obra(5L, "Ruta 34");
        when(obraRepository.existsById(5L)).thenReturn(true);
        when(tramoRepository.findByObraIdOrderByIdAsc(5L))
                .thenReturn(List.of(tramo(20L, obra, "Tramo norte"), tramo(21L, obra, "Tramo sur")));

        List<TramoResponse> resultado = obraService.listarTramos(5L);

        assertThat(resultado).extracting(TramoResponse::id).containsExactly(20L, 21L);
        assertThat(resultado).extracting(TramoResponse::obraId).containsOnly(5L);
    }

    @Test
    void listarTramosDeObraInexistenteEs404YNoConsultaTramos() {
        when(obraRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> obraService.listarTramos(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(tramoRepository, never()).findByObraIdOrderByIdAsc(any());
    }

    // ---------- cronograma de la obra ----------

    @Test
    void elCronogramaDeLaObraDevuelveLasFasesEnElOrdenDelRepository() {
        Obra obra = obra(5L, "Ruta 34");
        Tramo tramo = tramo(20L, obra, "Tramo norte");
        when(obraRepository.existsById(5L)).thenReturn(true);
        when(faseRepository.findByTramoObraIdOrderByFechaInicioPlanificadaAsc(5L)).thenReturn(List.of(
                fase(30L, tramo, EstadoFase.COMPLETADA, 100),
                fase(31L, tramo, EstadoFase.PENDIENTE, 0)));

        List<FaseResponse> resultado = obraService.cronogramaDeObra(5L);

        assertThat(resultado).extracting(FaseResponse::id).containsExactly(30L, 31L);
    }

    @Test
    void elCronogramaDeUnaObraInexistenteEs404YNoConsultaFases() {
        when(obraRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> obraService.cronogramaDeObra(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(faseRepository, never()).findByTramoObraIdOrderByFechaInicioPlanificadaAsc(any());
    }
}
