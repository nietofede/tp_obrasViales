package com.obrasviales.ms_obras.repository;

import com.obrasviales.ms_obras.domain.Fase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaseRepository extends JpaRepository<Fase, Long> {

    List<Fase> findByFaseAnteriorId(Long faseAnteriorId);

    List<Fase> findByTramoId(Long tramoId);

    List<Fase> findByTramoIdOrderByFechaInicioPlanificadaAsc(Long tramoId);
}