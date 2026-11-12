package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.EvaluacionClimatica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluacionClimaticaRepository extends JpaRepository<EvaluacionClimatica, Long> {

    @Query("SELECT e FROM EvaluacionClimatica e WHERE e.ordenTrabajo.id = :ordenId ORDER BY e.fechaEvaluacion DESC")
    List<EvaluacionClimatica> obtenerHistorialPorOrden(@Param("ordenId") Long ordenId);
}