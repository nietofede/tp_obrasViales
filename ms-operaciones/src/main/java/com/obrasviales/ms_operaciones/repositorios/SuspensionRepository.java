package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.Suspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuspensionRepository extends JpaRepository<Suspension, Long> {
    long countByFaseIdAndVigenteTrue(Long faseId);

    List<Suspension> findAllByOrderByFechaDesc();

    List<Suspension> findByFaseIdOrderByFechaDesc(Long faseId);

    List<Suspension> findByOrdenTrabajoIdOrderByFechaDesc(Long ordenTrabajoId);

    // Tras la decision del PLANIFICADOR el conteo de la fase vuelve a empezar (el historial se conserva).
    @Modifying
    @Query("update Suspension s set s.vigente = false where s.faseId = :faseId and s.vigente = true")
    int marcarNoVigentes(@Param("faseId") Long faseId);
}
