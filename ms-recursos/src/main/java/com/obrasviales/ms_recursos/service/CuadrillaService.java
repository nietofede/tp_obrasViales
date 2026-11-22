package com.obrasviales.ms_recursos.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.CuadrillaResponse;
import com.obrasviales.ms_recursos.dto.CatalogoDtos.UbicacionRequest;
import com.obrasviales.ms_recursos.entity.Cuadrilla;
import com.obrasviales.ms_recursos.entity.Operario;
import com.obrasviales.ms_recursos.repository.CuadrillaRepository;
import com.obrasviales.ms_recursos.repository.MaquinaRepository;
import com.obrasviales.ms_recursos.repository.OperarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Consulta de cuadrillas y check-in de ubicación. Las cuadrillas se cargan con Flyway; la última ubicación
// es la que usa RN-07 para calcular el viaje al tramo.
@Service
public class CuadrillaService {

    private final CuadrillaRepository cuadrillaRepository;
    private final OperarioRepository operarioRepository;
    private final MaquinaRepository maquinaRepository;

    public CuadrillaService(CuadrillaRepository cuadrillaRepository,
            OperarioRepository operarioRepository,
            MaquinaRepository maquinaRepository) {
        this.cuadrillaRepository = cuadrillaRepository;
        this.operarioRepository = operarioRepository;
        this.maquinaRepository = maquinaRepository;
    }

    @Transactional(readOnly = true)
    public List<CuadrillaResponse> listar() {
        return cuadrillaRepository.findAll().stream().map(this::armar).toList();
    }

    // El capataz informa dónde está su cuadrilla. Solo puede hacerlo el CAPATAZ de
    // esa cuadrilla.
    @Transactional
    public CuadrillaResponse registrarUbicacion(Long cuadrillaId, UbicacionRequest req, String usuarioKeycloak) {
        Cuadrilla c = cuadrillaRepository.findById(cuadrillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuadrilla no encontrada: " + cuadrillaId));

        Operario capataz = operarioRepository.findByKeycloakUsername(usuarioKeycloak).orElse(null);

        boolean esCapatazDeLaCuadrilla = capataz != null
                && "CAPATAZ".equals(capataz.getRolOperativo())
                && capataz.getCuadrilla() != null
                && capataz.getCuadrilla().getId().equals(c.getId());

        if (!esCapatazDeLaCuadrilla) {
            throw new AccessDeniedException("Solo el capataz de la cuadrilla puede registrar su ubicación");
        }
        c.setUltimaLatitud(req.latitud());
        c.setUltimaLongitud(req.longitud());
        c.setFechaCheckin(LocalDateTime.now());
        return armar(cuadrillaRepository.save(c));
    }

    private CuadrillaResponse armar(Cuadrilla c) {
        return CuadrillaResponse.desde(c,
                operarioRepository.findByCuadrillaId(c.getId()),
                maquinaRepository.findByCuadrillaId(c.getId()));
    }
}
