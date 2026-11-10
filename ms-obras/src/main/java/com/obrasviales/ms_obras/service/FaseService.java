package com.obrasviales.ms_obras.service;

import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.repository.FaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FaseService {

    private final FaseRepository faseRepository;

    @Transactional
    public Fase iniciarFase(Long faseId) {
        Fase fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new ReglaNegocioException("No existe la fase con id " + faseId));

        if (fase.getEstado() != EstadoFase.PENDIENTE) {
            throw new ReglaNegocioException(
                    "Solo se puede iniciar una fase PENDIENTE. Estado actual: " + fase.getEstado());
        }

        // RN-01: una fase no inicia si la anterior no está completada
        Fase anterior = fase.getFaseAnterior();
        if (anterior != null && anterior.getEstado() != EstadoFase.COMPLETADA) {
            throw new ReglaNegocioException(
                    "No se puede iniciar la fase: la fase anterior no está completada");
        }

        fase.setEstado(EstadoFase.EN_CURSO);
        return faseRepository.save(fase);
    }
}