package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.FaseEnRiesgo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaseEnRiesgoRepository extends JpaRepository<FaseEnRiesgo, Long> {
    boolean existsByFaseIdAndResueltaFalse(Long faseId);
}
