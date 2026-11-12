package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.dto.ObraDetalleResponse;
import com.obrasviales.ms_obras.dto.ObraResumenResponse;
import com.obrasviales.ms_obras.dto.TramoResponse;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.ObraRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObraService {

    private final ObraRepository obraRepository;
    private final TramoRepository tramoRepository;
    private final FaseRepository faseRepository;

    @Transactional
    public Obra crearObra(String nombre, String descripcion) {
        Obra obra = new Obra();
        obra.setNombre(nombre);
        obra.setDescripcion(descripcion);
        return obraRepository.save(obra);
    }

    @Transactional
    public Obra actualizarObra(Long obraId, String nombre, String descripcion) {
        Obra obra = buscarObra(obraId);
        obra.setNombre(nombre);
        obra.setDescripcion(descripcion);
        return obraRepository.save(obra);
    }

    @Transactional(readOnly = true)
    public List<ObraResumenResponse> listarObras() {
        return obraRepository.findAll(Sort.by("id")).stream()
                .map(obra -> {
                    List<Fase> fases = faseRepository.findByTramoObraId(obra.getId());
                    return new ObraResumenResponse(obra.getId(), obra.getNombre(),
                            obra.getDescripcion(), estadoDe(fases), avanceDe(fases));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ObraDetalleResponse obtenerObra(Long obraId) {
        Obra obra = buscarObra(obraId);
        List<Fase> fases = faseRepository.findByTramoObraIdOrderByFechaInicioPlanificadaAsc(obraId);
        List<ObraDetalleResponse.TramoConFases> tramos =
                tramoRepository.findByObraIdOrderByIdAsc(obraId).stream()
                        .map(tramo -> new ObraDetalleResponse.TramoConFases(
                                TramoResponse.desde(tramo),
                                fases.stream()
                                        .filter(f -> f.getTramo().getId().equals(tramo.getId()))
                                        .map(FaseResponse::desde)
                                        .toList()))
                        .toList();
        return new ObraDetalleResponse(obra.getId(), obra.getNombre(), obra.getDescripcion(),
                estadoDe(fases), avanceDe(fases), tramos);
    }

    @Transactional
    public Tramo crearTramo(Long obraId, String nombre, BigDecimal superficieM2,
                            Double latitud, Double longitud) {
        Obra obra = buscarObra(obraId);

        Tramo tramo = new Tramo();
        tramo.setObra(obra);
        tramo.setNombre(nombre);
        tramo.setSuperficieM2(superficieM2);
        tramo.setLatitud(latitud);
        tramo.setLongitud(longitud);
        return tramoRepository.save(tramo);
    }

    @Transactional(readOnly = true)
    public List<TramoResponse> listarTramos(Long obraId) {
        if (!obraRepository.existsById(obraId)) {
            throw new RecursoNoEncontradoException("No existe la obra con id " + obraId);
        }
        return tramoRepository.findByObraIdOrderByIdAsc(obraId).stream()
                .map(TramoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public Tramo obtenerTramo(Long tramoId) {
        return buscarTramo(tramoId);
    }

    // Los materiales se calculan al consultar, asi que al cambiar la superficie se recalculan solos
    @Transactional
    public Tramo actualizarTramo(Long tramoId, String nombre, BigDecimal superficieM2,
                                 Double latitud, Double longitud) {
        Tramo tramo = buscarTramo(tramoId);
        tramo.setNombre(nombre);
        tramo.setSuperficieM2(superficieM2);
        tramo.setLatitud(latitud);
        tramo.setLongitud(longitud);
        return tramoRepository.save(tramo);
    }

    @Transactional(readOnly = true)
    public List<FaseResponse> cronogramaDeObra(Long obraId) {
        if (!obraRepository.existsById(obraId)) {
            throw new RecursoNoEncontradoException("No existe la obra con id " + obraId);
        }
        return faseRepository.findByTramoObraIdOrderByFechaInicioPlanificadaAsc(obraId).stream()
                .map(FaseResponse::desde)
                .toList();
    }

    private Obra buscarObra(Long obraId) {
        return obraRepository.findById(obraId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la obra con id " + obraId));
    }

    private Tramo buscarTramo(Long tramoId) {
        return tramoRepository.findById(tramoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el tramo con id " + tramoId));
    }

    // Promedio del avance de sus fases
    private int avanceDe(List<Fase> fases) {
        if (fases.isEmpty()) {
            return 0;
        }
        return (int) Math.round(fases.stream().mapToInt(Fase::getAvancePorcentaje).average().orElse(0));
    }

    private String estadoDe(List<Fase> fases) {
        if (fases.isEmpty()) {
            return "PLANIFICADA";
        }
        if (fases.stream().allMatch(f -> f.getEstado() == EstadoFase.COMPLETADA)) {
            return "COMPLETADA";
        }
        boolean empezo = fases.stream().anyMatch(f ->
                f.getEstado() == EstadoFase.EN_CURSO
                        || f.getEstado() == EstadoFase.COMPLETADA
                        || f.getAvancePorcentaje() > 0);
        return empezo ? "EN_EJECUCION" : "PLANIFICADA";
    }
}