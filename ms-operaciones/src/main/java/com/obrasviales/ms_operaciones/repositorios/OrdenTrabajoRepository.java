package com.obrasviales.ms_operaciones.repositorios;

import com.obrasviales.ms_operaciones.modelo.EstadoOrden;
import com.obrasviales.ms_operaciones.modelo.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long> {
    List<OrdenTrabajo> findByFechaProgramadaAndEstado(LocalDate fecha, EstadoOrden estado);
    List<OrdenTrabajo> findByFaseId(Long faseId);
    List<OrdenTrabajo> findByFechaProgramada(LocalDate fecha);
    List<OrdenTrabajo> findByEstado(EstadoOrden estado);
}