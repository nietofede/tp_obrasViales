package com.obrasviales.ms_operaciones.servicios;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_operaciones.dtos.UmbralClimaticoRequest;
import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.modelo.UmbralClimatico;
import com.obrasviales.ms_operaciones.repositorios.UmbralClimaticoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UmbralClimaticoService {

    private final UmbralClimaticoRepository umbralRepository;

    @Transactional
    public UmbralClimatico guardarOActualizarUmbral(TipoTrabajo tipoTrabajo, UmbralClimaticoRequest request) {
        UmbralClimatico umbral = umbralRepository.findByTipoTrabajo(tipoTrabajo)
                .orElse(new UmbralClimatico());

        umbral.setTipoTrabajo(tipoTrabajo);
        umbral.setHumedadSueloMaxima(request.humedadSueloMaxima());
        umbral.setProbPrecipitacionMaxima(request.probPrecipitacionMaxima());
        umbral.setTemperaturaMinima(request.temperaturaMinima());

        return umbralRepository.save(umbral);
    }

    @Transactional(readOnly = true)
    public UmbralClimatico obtenerUmbralPorTipo(TipoTrabajo tipoTrabajo) {
        return umbralRepository.findByTipoTrabajo(tipoTrabajo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay umbrales configurados para el tipo de trabajo: " + tipoTrabajo));
    }

    @Transactional(readOnly = true)
    public List<UmbralClimatico> listarUmbrales() {
        return umbralRepository.findAll();
    }
}
