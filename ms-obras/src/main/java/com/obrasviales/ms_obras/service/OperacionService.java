package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.DatosOperacionResponse;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.repository.FaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperacionService {

    private final FaseRepository faseRepository;
    private final MaterialService materialService;

    // Datos que ms-operaciones necesita de una fase: tramo, coordenadas,
    // tipo de trabajo, materiales (RN-03) y si la fase previa está completa
    @Transactional(readOnly = true)
    public DatosOperacionResponse obtenerDatosOperacion(Long faseId) {
        Fase fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la fase con id " + faseId));

        Tramo tramo = fase.getTramo();
        Fase anterior = fase.getFaseAnterior();

        // Sin fase anterior no hay nada que bloquee: cuenta como completada (igual que RN-01)
        boolean faseAnteriorCompletada = anterior == null
                || anterior.getEstado() == EstadoFase.COMPLETADA;

        List<MaterialCalculado> materiales =
                materialService.calcularMateriales(fase.getTipo(), tramo.getSuperficieM2());

        return new DatosOperacionResponse(
                fase.getId(),
                fase.getTipo(),
                fase.getEstado(),
                faseAnteriorCompletada,
                tramo.getId(),
                tramo.getNombre(),
                tramo.getSuperficieM2(),
                tramo.getLatitud(),
                tramo.getLongitud(),
                materiales,
                anterior == null ? null : anterior.getFechaFinPlanificada());
    }
}