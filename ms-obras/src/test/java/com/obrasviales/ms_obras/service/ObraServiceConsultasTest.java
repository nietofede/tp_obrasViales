package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.ObraResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
import com.obrasviales.ms_obras.repository.ObraRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
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

    // Caso normal: lista las obras en el orden que entrega el repository
    @Test
    void listarObrasDevuelveLasObras() {
        when(obraRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(obra(1L, "Ruta 34"), obra(2L, "Av. Libertad")));

        List<ObraResponse> resultado = obraService.listarObras();

        assertThat(resultado).extracting(ObraResponse::id).containsExactly(1L, 2L);
        assertThat(resultado.get(1).nombre()).isEqualTo("Av. Libertad");
    }

    // Caso límite: sin obras devuelve una lista vacía, no un error
    @Test
    void listarObrasSinObrasDevuelveListaVacia() {
        when(obraRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(obraService.listarObras()).isEmpty();
    }

    // Caso normal: una obra existente se devuelve con sus datos
    @Test
    void obtenerObraExistenteDevuelveLaObra() {
        when(obraRepository.findById(1L)).thenReturn(Optional.of(obra(1L, "Ruta 34")));

        ObraResponse resultado = obraService.obtenerObra(1L);

        assertThat(resultado.id()).isEqualTo(1L);
        assertThat(resultado.nombre()).isEqualTo("Ruta 34");
    }

    // Rechazo: una obra inexistente lanza 404
    @Test
    void obtenerObraInexistenteLanzaNoEncontrado() {
        when(obraRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> obraService.obtenerObra(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    // Caso normal: lista los tramos de la obra, con el id de la obra en cada uno
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

    // Rechazo: una obra inexistente lanza 404 y ni siquiera consulta los tramos
    @Test
    void listarTramosDeObraInexistenteLanzaNoEncontradoYNoConsultaTramos() {
        when(obraRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> obraService.listarTramos(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");

        verify(tramoRepository, never()).findByObraIdOrderByIdAsc(any());
    }
}