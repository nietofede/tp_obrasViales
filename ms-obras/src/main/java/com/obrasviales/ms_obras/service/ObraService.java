package com.obrasviales.ms_obras.service;

import com.obrasviales.ms_obras.domain.Obra;
import com.obrasviales.ms_obras.repository.ObraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObraService {

    private final ObraRepository obraRepository;

    @Transactional
    public Obra crearObra(String nombre, String descripcion) {
        Obra obra = new Obra();
        obra.setNombre(nombre);
        obra.setDescripcion(descripcion);
        return obraRepository.save(obra);
    }
}