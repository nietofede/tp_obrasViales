package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.Suspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuspensionRepository extends JpaRepository<Suspension, Long> {
    long countByFaseIdAndVigenteTrue(Long faseId);
}
