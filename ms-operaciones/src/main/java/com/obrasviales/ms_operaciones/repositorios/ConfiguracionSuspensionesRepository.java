package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.ConfiguracionSuspensiones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionSuspensionesRepository extends JpaRepository<ConfiguracionSuspensiones, Long> {
}
