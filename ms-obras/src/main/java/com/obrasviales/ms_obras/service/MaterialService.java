package com.obrasviales.ms_obras.service;

import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.repository.ConsumoMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final ConsumoMaterialRepository consumoMaterialRepository;

    // RN-03: los materiales se calculan según la superficie del tramo
    @Transactional(readOnly = true)
    public List<MaterialCalculado> calcularMateriales(TipoFase tipoFase, BigDecimal superficieM2) {
        if (tipoFase == null) {
            throw new ReglaNegocioException("El tipo de fase es obligatorio");
        }
        if (superficieM2 == null || superficieM2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("La superficie del tramo debe ser mayor que 0");
        }

        return consumoMaterialRepository.findByTipoFase(tipoFase).stream()
                .map(consumo -> new MaterialCalculado(
                        consumo.getMaterial(),
                        superficieM2.multiply(consumo.getConsumoPorM2()).setScale(2, RoundingMode.HALF_UP),
                        consumo.getUnidad()))
                .toList();
    }
}