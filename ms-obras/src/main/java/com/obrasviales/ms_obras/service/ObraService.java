package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.repository.ObraRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ObraService {

    private final ObraRepository obraRepository;
    private final TramoRepository tramoRepository;

    @Transactional
    public Obra crearObra(String nombre, String descripcion) {
        Obra obra = new Obra();
        obra.setNombre(nombre);
        obra.setDescripcion(descripcion);
        return obraRepository.save(obra);
    }

    @Transactional
    public Tramo crearTramo(Long obraId, String nombre, BigDecimal superficieM2,
                            Double latitud, Double longitud) {
        Obra obra = obraRepository.findById(obraId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la obra con id " + obraId));

        Tramo tramo = new Tramo();
        tramo.setObra(obra);
        tramo.setNombre(nombre);
        tramo.setSuperficieM2(superficieM2);
        tramo.setLatitud(latitud);
        tramo.setLongitud(longitud);
        return tramoRepository.save(tramo);
    }
}