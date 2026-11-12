package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.Cuadrilla;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, Long> {
    boolean existsByNombre(String nombre);
}

