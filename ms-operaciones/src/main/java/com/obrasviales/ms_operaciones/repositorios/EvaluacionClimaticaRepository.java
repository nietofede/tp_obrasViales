package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluacionClimaticaRepository extends JpaRepository<EvaluacionClimatica, Long> {
    List<EvaluacionClimatica> findByOrdenTrabajoIdOrderByFechaEvaluacionDesc(Long ordenTrabajoId);
}
