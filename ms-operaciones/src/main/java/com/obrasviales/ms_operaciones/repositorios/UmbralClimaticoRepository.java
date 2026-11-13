package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.TipoTrabajo;
import com.obrasviales.ms_operaciones.modelo.UmbralClimatico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UmbralClimaticoRepository extends JpaRepository<UmbralClimatico, Long> {
    Optional<UmbralClimatico> findByTipoTrabajo(TipoTrabajo tipoTrabajo);
}