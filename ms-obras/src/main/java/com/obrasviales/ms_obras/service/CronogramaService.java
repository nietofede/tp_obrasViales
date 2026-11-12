package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.dto.FaseResponse;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CronogramaService {

    private final TramoRepository tramoRepository;
    private final FaseRepository faseRepository;

    @Transactional(readOnly = true)
    public List<FaseResponse> cronogramaDelTramo(Long tramoId) {
        if (!tramoRepository.existsById(tramoId)) {
            throw new RecursoNoEncontradoException("No existe el tramo con id " + tramoId);
        }
        return faseRepository.findByTramoIdOrderByFechaInicioPlanificadaAsc(tramoId).stream()
                .map(FaseResponse::desde)
                .toList();
    }
}