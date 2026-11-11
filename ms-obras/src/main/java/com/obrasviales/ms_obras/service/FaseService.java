package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FaseService {

    private final FaseRepository faseRepository;
    private final TramoRepository tramoRepository;

    @Transactional
    public Fase crearFase(Long tramoId, TipoFase tipo, LocalDate inicio, LocalDate fin,
                          Long faseAnteriorId) {
        Tramo tramo = tramoRepository.findById(tramoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el tramo con id " + tramoId));

        if (fin.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "La fecha de fin no puede ser anterior a la de inicio", "FASE-FECHAS");
        }

        Fase anterior = null;
        if (faseAnteriorId != null) {
            anterior = faseRepository.findById(faseAnteriorId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe la fase anterior con id " + faseAnteriorId));

            if (!anterior.getTramo().getId().equals(tramoId)) {
                throw new ReglaNegocioException(
                        "La fase anterior debe pertenecer al mismo tramo", "FASE-TRAMO");
            }
            if (!faseRepository.findByFaseAnteriorId(faseAnteriorId).isEmpty()) {
                throw new ConflictoEstadoException("La fase anterior ya tiene una fase siguiente");
            }
            if (inicio.isBefore(anterior.getFechaFinPlanificada())) {
                throw new ReglaNegocioException(
                        "La fase no puede iniciar antes de que termine la fase anterior", "RN-02");
            }
        }

        Fase fase = new Fase();
        fase.setTramo(tramo);
        fase.setTipo(tipo);
        fase.setFechaInicioPlanificada(inicio);
        fase.setFechaFinPlanificada(fin);
        fase.setFaseAnterior(anterior);
        return faseRepository.save(fase);
    }

    @Transactional
    public Fase iniciarFase(Long faseId) {
        Fase fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la fase con id " + faseId));

        if (fase.getEstado() != EstadoFase.PENDIENTE) {
            throw new ConflictoEstadoException(
                    "Solo se puede iniciar una fase PENDIENTE. Estado actual: " + fase.getEstado());
        }

        // RN-01: una fase no inicia si la anterior no está completada
        Fase anterior = fase.getFaseAnterior();
        if (anterior != null && anterior.getEstado() != EstadoFase.COMPLETADA) {
            throw new ReglaNegocioException(
                    "No se puede iniciar la fase: la fase anterior no está completada", "RN-01");
        }

        fase.setEstado(EstadoFase.EN_CURSO);
        return faseRepository.save(fase);
    }

    // RN-02: al reprogramar una fase se corren en cascada las que dependen de ella
    @Transactional
    public Fase reprogramarFase(Long faseId, LocalDate nuevaFechaInicio) {
        if (nuevaFechaInicio == null) {
            throw new ReglaNegocioException("La nueva fecha de inicio es obligatoria", "RN-02");
        }

        Fase fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la fase con id " + faseId));

        if (fase.getEstado() == EstadoFase.COMPLETADA) {
            throw new ConflictoEstadoException("No se puede reprogramar una fase COMPLETADA");
        }

        Fase anterior = fase.getFaseAnterior();
        if (anterior != null && nuevaFechaInicio.isBefore(anterior.getFechaFinPlanificada())) {
            throw new ReglaNegocioException(
                    "La fase no puede iniciar antes de que termine la fase anterior", "RN-02");
        }

        long dias = ChronoUnit.DAYS.between(fase.getFechaInicioPlanificada(), nuevaFechaInicio);
        desplazarEnCascada(fase, dias, new HashSet<>());
        return fase;
    }

    private void desplazarEnCascada(Fase fase, long dias, Set<Long> visitadas) {
        if (!visitadas.add(fase.getId())) {
            throw new ReglaNegocioException("Se detectó un ciclo en la dependencia entre fases", "RN-02");
        }

        fase.setFechaInicioPlanificada(fase.getFechaInicioPlanificada().plusDays(dias));
        fase.setFechaFinPlanificada(fase.getFechaFinPlanificada().plusDays(dias));
        faseRepository.save(fase);

        for (Fase dependiente : faseRepository.findByFaseAnteriorId(fase.getId())) {
            desplazarEnCascada(dependiente, dias, visitadas);
        }
    }
}