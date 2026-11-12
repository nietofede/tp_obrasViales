package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.ConsumoMaterial;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.repository.ConsumoMaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private ConsumoMaterialRepository consumoMaterialRepository;

    @InjectMocks
    private MaterialService materialService;

    private ConsumoMaterial consumo(TipoFase tipo, String material, String consumoPorM2, String unidad) {
        ConsumoMaterial consumo = new ConsumoMaterial();
        consumo.setTipoFase(tipo);
        consumo.setMaterial(material);
        consumo.setConsumoPorM2(new BigDecimal(consumoPorM2));
        consumo.setUnidad(unidad);
        return consumo;
    }

    // RN-03 caso normal: 1000 m2 x 0,25 m3/m2 = 250 m3
    @Test
    void calculaMaterialesSegunLaSuperficieDelTramo() {
        when(consumoMaterialRepository.findByTipoFase(TipoFase.BASE))
                .thenReturn(List.of(consumo(TipoFase.BASE, "Piedra triturada", "0.2500", "m3")));

        List<MaterialCalculado> resultado =
                materialService.calcularMateriales(TipoFase.BASE, new BigDecimal("1000"));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).material()).isEqualTo("Piedra triturada");
        assertThat(resultado.get(0).cantidad()).isEqualByComparingTo("250.00");
        assertThat(resultado.get(0).unidad()).isEqualTo("m3");
    }

    // RN-03 caso límite: el redondeo a 2 decimales sube en el borde (0,125 -> 0,13)
    @Test
    void redondeaADosDecimalesHaciaArribaEnElBorde() {
        when(consumoMaterialRepository.findByTipoFase(TipoFase.SUBBASE))
                .thenReturn(List.of(consumo(TipoFase.SUBBASE, "Suelo-cemento", "0.1250", "m3")));

        List<MaterialCalculado> resultado =
                materialService.calcularMateriales(TipoFase.SUBBASE, new BigDecimal("1"));

        assertThat(resultado.get(0).cantidad()).isEqualByComparingTo("0.13");
    }

    // RN-03 caso límite: un tipo de fase sin consumos cargados no es un error
    @Test
    void devuelveListaVaciaSiElTipoDeFaseNoTieneConsumos() {
        when(consumoMaterialRepository.findByTipoFase(TipoFase.RELEVAMIENTO)).thenReturn(List.of());

        List<MaterialCalculado> resultado =
                materialService.calcularMateriales(TipoFase.RELEVAMIENTO, new BigDecimal("500"));

        assertThat(resultado).isEmpty();
    }

    // RN-03 rechazo: la superficie 0 no es válida y no se consulta la base
    @Test
    void rechazaSuperficieCero() {
        assertThatThrownBy(() -> materialService.calcularMateriales(TipoFase.BASE, BigDecimal.ZERO))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("RN-03"))
                .hasMessageContaining("mayor que 0");

        verify(consumoMaterialRepository, never()).findByTipoFase(any(TipoFase.class));
    }

    // RN-03 rechazo: la superficie negativa tampoco es válida
    @Test
    void rechazaSuperficieNegativa() {
        assertThatThrownBy(() -> materialService.calcularMateriales(TipoFase.BASE, new BigDecimal("-10")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigoRegla()).isEqualTo("RN-03"))
                .hasMessageContaining("mayor que 0");

        verify(consumoMaterialRepository, never()).findByTipoFase(any(TipoFase.class));
    }
}