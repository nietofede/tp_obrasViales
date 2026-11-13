package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.ComposicionResponse;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoRequest;
import com.obrasviales.ms_recursos.dto.ConfiguracionDtos.DesplazamientoResponse;
import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import com.obrasviales.ms_recursos.entity.ConfiguracionDesplazamiento;
import com.obrasviales.ms_recursos.repository.ComposicionMinimaRepository;
import com.obrasviales.ms_recursos.repository.ConfiguracionDesplazamientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

// Parámetros que el ADMIN puede ajustar sin tocar el código: composición mínima por tipo de trabajo (RN-06) y
// límites de viaje y transporte (RN-07 y RN-08).
@Service
public class ConfiguracionService {

    // Vocabulario de tipos de trabajo acordado por el equipo (igual al TipoFase de Obras).
    static final Set<String> TIPOS_DE_TRABAJO =
            Set.of("RELEVAMIENTO", "BASE", "SUBBASE", "CARPETA_ASFALTICA", "LOSA_HORMIGON");

    private final ComposicionMinimaRepository composicionRepository;
    private final ConfiguracionDesplazamientoRepository desplazamientoRepository;

    public ConfiguracionService(ComposicionMinimaRepository composicionRepository,
                                ConfiguracionDesplazamientoRepository desplazamientoRepository) {
        this.composicionRepository = composicionRepository;
        this.desplazamientoRepository = desplazamientoRepository;
    }

    @Transactional(readOnly = true)
    public List<ComposicionResponse> listarComposiciones() {
        return composicionRepository.findAll().stream().map(ComposicionResponse::desde).toList();
    }

    @Transactional
    public ComposicionResponse actualizarComposicion(String tipoTrabajo, ComposicionRequest req) {
        if (!TIPOS_DE_TRABAJO.contains(tipoTrabajo)) {
            throw new RecursoNoEncontradoException("Tipo de trabajo desconocido: " + tipoTrabajo);
        }
        boolean pideMaquinas = req.cantidadMaquinas() > 0;
        boolean sinTipo = req.tipoMaquinaRequerida() == null || req.tipoMaquinaRequerida().isBlank();
        if (pideMaquinas && sinTipo) {
            throw new ReglaNegocioException(
                    "Si se piden máquinas hay que indicar el tipo de máquina requerida", "RN-06");
        }
        ComposicionMinimaTrabajo c = composicionRepository.findById(tipoTrabajo)
                .orElseGet(() -> {
                    ComposicionMinimaTrabajo nueva = new ComposicionMinimaTrabajo();
                    nueva.setTipoTrabajo(tipoTrabajo);
                    return nueva;
                });
        c.setMinOperarios(req.minOperarios());
        c.setCantidadMaquinas(req.cantidadMaquinas());
        c.setTipoMaquinaRequerida(sinTipo ? null : req.tipoMaquinaRequerida());
        c.setRequiereMezcla(req.requiereMezcla());
        return ComposicionResponse.desde(composicionRepository.save(c));
    }

    @Transactional(readOnly = true)
    public DesplazamientoResponse obtenerDesplazamiento() {
        return DesplazamientoResponse.desde(desplazamiento());
    }

    @Transactional
    public DesplazamientoResponse actualizarDesplazamiento(DesplazamientoRequest req) {
        if (req.viajeMaximoCuadrillaMin() > req.duracionJornadaMin()) {
            throw new ReglaNegocioException(
                    "El viaje máximo de la cuadrilla no puede superar la duración de la jornada", "RN-07");
        }
        ConfiguracionDesplazamiento c = desplazamiento();
        c.setViajeMaximoCuadrillaMin(req.viajeMaximoCuadrillaMin());
        c.setDuracionJornadaMin(req.duracionJornadaMin());
        c.setVentanaTransporteMezclaMin(req.ventanaTransporteMezclaMin());
        c.setMargenCamionMin(req.margenCamionMin());
        return DesplazamientoResponse.desde(desplazamientoRepository.save(c));
    }

    // La configuración es una única fila (id = 1) sembrada por la migración V2.
    private ConfiguracionDesplazamiento desplazamiento() {
        return desplazamientoRepository.findById(1L)
                .orElseThrow(() -> new RecursoNoEncontradoException("Falta la configuración de desplazamiento"));
    }
}
