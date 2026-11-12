package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaseEnRiesgoRepository extends JpaRepository<FaseEnRiesgo, Long> {
    boolean existsByFaseIdAndResueltaFalse(Long faseId);

    Optional<FaseEnRiesgo> findByFaseIdAndResueltaFalse(Long faseId);

    List<FaseEnRiesgo> findByResueltaFalseOrderByCreadoAsc();
}
