package com.obrasviales.ms_obras.repository;

import com.obrasviales.ms_obras.domain.Obra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObraRepository extends JpaRepository<Obra, Long> {
}