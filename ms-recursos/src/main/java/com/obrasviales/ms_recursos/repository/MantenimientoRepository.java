package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {

    @Query("""
        SELECT COUNT(m) > 0 FROM Mantenimiento m
        WHERE m.maquina.id = :maquinaId
          AND :fecha BETWEEN m.fechaInicio AND m.fechaFin
    """)
    boolean tieneMantenimientoEnFecha(@Param("maquinaId") Long maquinaId, @Param("fecha") LocalDate fecha);
}