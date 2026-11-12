package com.obrasviales.ms_recursos.repository;

import com.obrasviales.ms_recursos.entity.ComposicionMinimaTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComposicionMinimaRepository extends JpaRepository<ComposicionMinimaTrabajo, String> {
}