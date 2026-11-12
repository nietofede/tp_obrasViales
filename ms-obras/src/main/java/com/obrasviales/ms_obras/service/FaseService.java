package com.obrasviales.ms_obras.service;

import com.obrasviales.commons.exception.ConflictoEstadoException;
import com.obrasviales.commons.exception.RecursoNoEncontradoException;
import com.obrasviales.commons.exception.ReglaNegocioException;
import com.obrasviales.ms_obras.domain.EstadoFase;
import com.obrasviales.ms_obras.domain.Fase;
import com.obrasviales.ms_obras.domain.TipoFase;
import com.obrasviales.ms_obras.domain.Tramo;
import com.obrasviales.ms_obras.dto.MaterialCalculado;
import com.obrasviales.ms_obras.repository.FaseRepository;
import com.obrasviales.ms_obras.repository.TramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FaseService {

    private final FaseRepository faseRepository;
    private final TramoRepository tramoRepository;
    private final MaterialService materialService;

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

    @Transactional(readOnly = true)
    public Fase obtenerFase(Long faseId) {
        return buscar(faseId);
    }

    // RN-03: materiales segun la superficie del tramo de la fase
    @Transactional(readOnly = true)
    public List<MaterialCalculado> materialesDeFase(Long faseId) {
        Fase fase = buscar(faseId);
        return materialService.calcularMateriales(fase.getTipo(), fase.getTramo().getSuperficieM2());
    }

    @Transactional
    public Fase iniciarFase(Long faseId) {
        Fase fase = buscar(faseId);

        if (fase.getEstado() != EstadoFase.PENDIENTE) {
            throw new ConflictoEstadoException(
                    "Solo se puede iniciar una fase PENDIENTE. Estado actual: " + fase.getEstado());
        }

        exigirAnteriorCompletada(fase);

        fase.setEstado(EstadoFase.EN_CURSO);
        return faseRepository.save(fase);
    }

    // Avance real: el primer avance inicia la fase (aplica RN-01) y al llegar a 100 queda COMPLETADA
    @Transactional
    public Fase registrarAvance(Long faseId, int porcentaje) {
        Fase fase = buscar(faseId);

        if (fase.getEstado() == EstadoFase.COMPLETADA) {
            throw new ConflictoEstadoException("La fase ya est\u00e1 COMPLETADA");
        }
        if (fase.getEstado() == EstadoFase.EN_RIESGO) {
            throw new ConflictoEstadoException(
                    "No se puede registrar avance en una fase EN_RIESGO: primero hay que reprogramarla");
        }
        if (porcentaje < fase.getAvancePorcentaje()) {
            throw new ReglaNegocioException(
                    "El avance no puede retroceder: actual " + fase.getAvancePorcentaje()
                            + " %, recibido " + porcentaje + " %", "AVANCE");
        }

        if (fase.getEstado() == EstadoFase.PENDIENTE) {
            exigirAnteriorCompletada(fase);
            fase.setEstado(EstadoFase.EN_CURSO);
        }

        fase.setAvancePorcentaje(porcentaje);
        if (porcentaje >= 100) {
            fase.setEstado(EstadoFase.COMPLETADA);
        }
        return faseRepository.save(fase);
    }

    // La marca Operaciones al llegar a N suspensiones; el PLANIFICADOR la resuelve reprogramando
    @Transactional
    public Fase marcarEnRiesgo(Long faseId) {
        Fase fase = buscar(faseId);

        if (fase.getEstado() == EstadoFase.COMPLETADA) {
            throw new ConflictoEstadoException("No se puede marcar en riesgo una fase COMPLETADA");
        }
        if (fase.getEstado() == EstadoFase.EN_RIESGO) {
            return fase;
        }

        fase.setEstado(EstadoFase.EN_RIESGO);
        return faseRepository.save(fase);
    }

    // RN-02: al reprogramar una fase se corren en cascada las que dependen de ella
    @Transactional
    public Fase reprogramarFase(Long faseId, LocalDate nuevaFechaInicio) {
        if (nuevaFechaInicio == null) {
            throw new ReglaNegocioException("La nueva fecha de inicio es obligatoria", "RN-02");
        }

        Fase fase = buscar(faseId);

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

        if (fase.getEstado() == EstadoFase.EN_RIESGO) {
            // el riesgo se resuelve reprogramando la fase
            fase.setEstado(EstadoFase.PENDIENTE);
            faseRepository.save(fase);
        }
        return fase;
    }

    private void desplazarEnCascada(Fase fase, long dias, Set<Long> visitadas) {
        if (!visitadas.add(fase.getId())) {
            throw new ReglaNegocioException("Se detect\u00f3 un ciclo en la dependencia entre fases", "RN-02");
        }

        fase.setFechaInicioPlanificada(fase.getFechaInicioPlanificada().plusDays(dias));
        fase.setFechaFinPlanificada(fase.getFechaFinPlanificada().plusDays(dias));
        faseRepository.save(fase);

        for (Fase dependiente : faseRepository.findByFaseAnteriorId(fase.getId())) {
            desplazarEnCascada(dependiente, dias, visitadas);
        }
    }

    private Fase buscar(Long faseId) {
        return faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la fase con id " + faseId));
    }

    // RN-01: una fase no inicia si la anterior no esta completada (409, como dice el cuestionario)
    private void exigirAnteriorCompletada(Fase fase) {
        Fase anterior = fase.getFaseAnterior();
        if (anterior != null && anterior.getEstado() != EstadoFase.COMPLETADA) {
            throw new ConflictoEstadoException(
                    "RN-01: no se puede iniciar la fase, la fase anterior no est\u00e1 completada");
        }
    }
}